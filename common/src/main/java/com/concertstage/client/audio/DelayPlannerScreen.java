package com.concertstage.client.audio;

import com.concertstage.client.ui.Tr;
import com.concertstage.audio.*;
import com.concertstage.audio.core.*;
import com.concertstage.audio.net.AudioNet;
import com.concertstage.client.ui.Ui;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;

/** Read-only world analysis. Apply alters console audio settings, never world blocks. */
public final class DelayPlannerScreen extends Screen {
  private static final int W = 590, H = 330, COLOR = Ui.PURPLE, MAP_W = 312, MAP_H = 214;
  private static final ExecutorService WORKER =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread t = new Thread(r, "CSC coverage solver");
            t.setDaemon(true);
            return t;
          });
  private final Screen parent;
  private final BlockPos consolePos;
  private final Set<BlockPos> mains = new LinkedHashSet<>();
  private int[] zone, stage;
  private int page, selected, sourceScroll, requestedStep = 4;
  private boolean predicted = true, worldGhost, stale = true;
  private final String[] names = {"Delay Tower 1", "Delay Tower 2", "Delay Tower 3"};
  /** 1.1.0: what each tab is for (under the title), in the player's language. */
  private static final String[] PAGE_HINTS = {
    "1. Tick the main arrays and set the stage point",
    "2. Mark out where the audience stands",
    "3. Check the coverage map",
    "4. Place the delay towers and send their delay"
  };
  private static final String[] PAGE_TIPS = {"planner_stage", "planner_audience", "planner_coverage", "planner_delay"};
  private String pendingName = "";
  private String delayModel = "csc_noryx_a1",
      status = Tr.t("Select mains and audience zone, then Analyze");
  private double green = .22, yellow = .12, orange = .05;
  private PlannerSampling sampling;
  private DelayCoverage.Result result;
  private List<DelayCoverage.Tower> draft = List.of();
  private ListTag restore;
  private Future<DelayCoverage.Result> future;
  private int generation, futureGeneration;
  private long recalcAt, pendingAt;
  private float pendingDelay = -1;
  private int pendingGroup, loadedCount, linkedCount;

  public DelayPlannerScreen(Screen parent, BlockPos pos, CompoundTag selection) {
    super(Component.literal(Tr.t("CSC Delay Planner")));
    this.parent = parent;
    consolePos = pos;
    var c = console();
    var saved = c == null ? new CompoundTag() : c.plannerPlan();
    zone = saved.getIntArray("Zone");
    if (zone.length != 5)
      zone =
          new int[] {
            pos.getX() - 24, pos.getZ() + 8, pos.getX() + 24, pos.getZ() + 128, pos.getY()
          };
    if (selection.contains("PlannerA") && selection.contains("PlannerB")) {
      var a = BlockPos.of(selection.getLong("PlannerA"));
      var b = BlockPos.of(selection.getLong("PlannerB"));
      zone =
          new int[] {
            Math.min(a.getX(), b.getX()),
            Math.min(a.getZ(), b.getZ()),
            Math.max(a.getX(), b.getX()),
            Math.max(a.getZ(), b.getZ()),
            Math.max(a.getY(), b.getY()) + 1
          };
    }
    stage = saved.getIntArray("Stage");
    if (stage.length != 3) stage = new int[] {pos.getX(), pos.getY(), pos.getZ()};
    if (selection.contains("PlannerStage")) {
      var s = BlockPos.of(selection.getLong("PlannerStage"));
      stage = new int[] {s.getX(), s.getY(), s.getZ()};
    }
    long[] chosen = selection.getLongArray("PlannerMains");
    if (chosen.length == 0) chosen = saved.getLongArray("Mains");
    for (long id : chosen) mains.add(BlockPos.of(id));
    if (mains.isEmpty() && c != null)
      for (var p : c.speakers())
        if (Minecraft.getInstance().level.getBlockEntity(p) instanceof SpeakerBlockEntity s
            && s.group() < 2
            && !s.cabinets().isEmpty()) mains.add(p);
    if (Arrays.equals(zone, saved.getIntArray("Zone")))
      restore = saved.getList("Towers", Tag.TAG_COMPOUND).copy();
  }

  private AudioConsoleBlockEntity console() {
    return RemoteConsoles.get(consolePos);
  }

  private int left() {
    return (width - W) / 2;
  }

  private int top() {
    return (height - H) / 2;
  }

  /** Help text put on the next button (key of {@code gui.concertstage.tip.*}), then cleared. */
  private String buttonTip;

  private void button(int x, int y, int w, String text, Runnable action) {
    var made = addRenderableWidget(
        new Ui.Button(
            left() + x,
            top() + y,
            w,
            19,
            () -> Component.literal(text),
            COLOR,
            Ui.Style.NORMAL,
            action));
    if (page >= 0 && buttonTip != null) Tr.help(made, buttonTip);
    buttonTip = null;
  }

  private void field(int x, int y, int w, String initial, Consumer<String> callback) {
    var e = new EditBox(font, left() + x, top() + y, w, 18, Component.empty());
    e.setMaxLength(12);
    e.setValue(initial);
    e.setResponder(callback);
    addRenderableWidget(e);
  }

  private void nameField() {
    var e = new EditBox(font, left() + 78, top() + 281, 246, 18, Component.literal(Tr.t("Bus label")));
    e.setMaxLength(32);
    e.setValue(names[selected]);
    e.setResponder(v -> names[selected] = v);
    addRenderableWidget(e);
  }

  private void integer(int x, int y, int value, IntConsumer set) {
    field(
        x,
        y,
        80,
        String.valueOf(value),
        s -> {
          try {
            int n = Integer.parseInt(s);
            if (Math.abs((long) n) <= 30000000) set.accept(n);
          } catch (NumberFormatException ignored) {
          }
        });
  }

  private void slider(
      int row,
      String label,
      int min,
      int max,
      int value,
      IntFunction<String> text,
      IntConsumer set) {
    addRenderableWidget(
        new Ui.Slider(
            left() + 338,
            top() + 72 + row * 29,
            238,
            23,
            Component.literal(label),
            min,
            max,
            value,
            COLOR,
            text,
            set) {
          @Override
          protected void applyValue() {
            set.accept(get());
          }
        });
  }

  @Override
  protected void init() {
    String[] tabs = {Tr.t("Stage Setup"), Tr.t("Audience Area"), Tr.t("Coverage"), Tr.t("Delay Configuration")};
    for (int i = 0; i < tabs.length; i++) {
      final int k = i;
      buttonTip = PAGE_TIPS[i];
      button(
          12 + i * 142,
          34,
          138,
          tabs[i],
          () -> {
            page = k;
            rebuildWidgets();
          });
    }
    button(12, H - 25, 94, Tr.t("Back"), this::onClose);
    buttonTip = "planner_save";
    button(112, H - 25, 105, Tr.t("Save plan"), this::save);
    buttonTip = "planner_ghost";
    button(
        223,
        H - 25,
        124,
        worldGhost ? Tr.t("Hide world ghost") : Tr.t("Show world ghost"),
        () -> {
          worldGhost = !worldGhost;
          publishGhost();
          rebuildWidgets();
        });
    if (page == 0) {
      var c = console();
      if (c != null) {
        var ids = c.speakers();
        for (int row = 0; row < 9 && row + sourceScroll < ids.size(); row++) {
          var p = ids.get(row + sourceScroll);
          String name =
              Minecraft.getInstance().level.getBlockEntity(p) instanceof SpeakerBlockEntity s
                      && s.firstModel() != null
                  ? s.firstModel().id()
                  : Tr.t("unloaded");
          button(
              12,
              68 + row * 24,
              310,
              (mains.contains(p) ? "[x] " : "[ ] ") + p.toShortString() + " " + name,
              () -> {
                if (!mains.remove(p)) mains.add(p);
                invalidate();
                rebuildWidgets();
              });
        }
        buttonTip = "planner_sources";
        button(
            12,
            286,
            150,
            Tr.t("Source list next"),
            () -> {
              sourceScroll = sourceScroll + 9 >= ids.size() ? 0 : sourceScroll + 9;
              rebuildWidgets();
            });
      }
      for (int i = 0; i < 3; i++) {
        final int k = i;
        integer(411, 90 + i * 34, stage[i], v -> stage[k] = v);
      }
      buttonTip = "planner_analyze";
      button(338, 230, 238, Tr.t("Analyze loaded world"), this::analyze);
    } else if (page == 1) {
      for (int i = 0; i < 5; i++) {
        final int k = i;
        integer(
            160,
            76 + i * 31,
            zone[i],
            v -> {
              zone[k] = v;
              invalidate();
            });
      }
      slider(
          0,
          Tr.t("Requested grid spacing"),
          2,
          16,
          requestedStep,
          v -> v + " m",
          v -> {
            requestedStep = v;
            invalidate();
          });
      button(
          338,
          109,
          238,
          Tr.t("Proposed model: ") + delayModel,
          () -> {
            delayModel = nextModel(delayModel);
            restore = null;
            rebuildWidgets();
          });
      buttonTip = "planner_analyze";
      button(338, 230, 238, Tr.t("Analyze / new proposals"), this::analyze);
    } else if (page == 2) {
      buttonTip = "planner_analyze";
      button(338, 65, 238, Tr.t("Analyze / new proposals"), this::analyze);
      buttonTip = "planner_map";
      button(
          338,
          89,
          238,
          predicted ? Tr.t("Map: with proposed delays") : Tr.t("Map: existing system"),
          () -> {
            predicted = !predicted;
            rebuildWidgets();
          });
      buttonTip = "planner_heatmap";
      button(
          338,
          113,
          238,
          DelayPlannerOverlay.heatmap() ? Tr.t("World heatmap ON") : Tr.t("World heatmap OFF"),
          () -> {
            DelayPlannerOverlay.heatmap(!DelayPlannerOverlay.heatmap());
            worldGhost = true;
            publishGhost();
            rebuildWidgets();
          });
      field(338, 155, 70, String.valueOf(green), s -> threshold(0, s));
      field(420, 155, 70, String.valueOf(yellow), s -> threshold(1, s));
      field(502, 155, 70, String.valueOf(orange), s -> threshold(2, s));
      buttonTip = "planner_edit";
      button(
          338,
          210,
          238,
          Tr.t("Edit tower placements"),
          () -> {
            page = 3;
            rebuildWidgets();
          });
    } else if (result != null && !draft.isEmpty()) {
      for (int i = 0; i < draft.size(); i++) {
        final int k = i;
        button(
            338 + i * 80,
            65,
            77,
            (i == selected ? "> " : "") + Tr.t("Tower ") + (i + 1),
            () -> {
              selected = k;
              rebuildWidgets();
            });
      }
      nameField();
      var t = draft.get(selected);
      button(
          338,
          90,
          238,
          Tr.t("Model: ") + t.model(),
          () -> {
            edit(q -> changeModel(q, nextModel(q.model())));
            rebuildWidgets();
          });
      slider(
          2,
          Tr.t("Acoustic centre height"),
          3,
          24,
          (int) t.height(),
          v -> v + " m",
          v -> edit(q -> q.move(result.grid(), result.sources(), q.x(), q.z(), v, q.modules())));
      slider(
          3,
          Tr.t("Modules"),
          1,
          12,
          t.modules(),
          String::valueOf,
          v -> edit(q -> q.move(result.grid(), result.sources(), q.x(), q.z(), q.height(), v)));
      slider(
          4,
          Tr.t("Target bus (all its linked arrays)"),
          1,
          6,
          t.group() + 1,
          String::valueOf,
          v -> edit(q -> copy(q, q.yaw(), q.enabled(), v - 1, q.delayMs())));
      field(
          338,
          229,
          92,
          String.format(java.util.Locale.ROOT, "%.2f", t.delayMs()),
          s -> {
            try {
              double d = Double.parseDouble(s.replace(',', '.'));
              if (Double.isFinite(d) && d >= 0 && d <= 1500)
                edit(q -> copy(q, q.yaw(), q.enabled(), q.group(), d));
            } catch (NumberFormatException ignored) {
            }
          });
      button(
          438,
          229,
          138,
          t.enabled() ? Tr.t("Ignore selected") : Tr.t("Enable selected"),
          () -> {
            edit(q -> copy(q, q.yaw(), !q.enabled(), q.group(), q.delayMs()));
            rebuildWidgets();
          });
      button(
          338,
          253,
          72,
          Tr.t("Yaw -15"),
          () -> edit(q -> copy(q, q.yaw() - 15, q.enabled(), q.group(), q.delayMs())));
      button(
          414,
          253,
          72,
          Tr.t("Yaw +15"),
          () -> edit(q -> copy(q, q.yaw() + 15, q.enabled(), q.group(), q.delayMs())));
      buttonTip = "planner_align";
      button(490, 253, 86, Tr.t("Auto align"), this::align);
      buttonTip = "planner_apply";
      button(338, 281, 238, Tr.t("Apply selected delay to bus"), this::apply);
    }
  }

  private String nextModel(String id) {
    var ids = SpeakerModels.ids().stream().filter(n -> !SpeakerModels.get(n).isSub()).toList();
    return ids.get((Math.max(0, ids.indexOf(id)) + 1) % ids.size());
  }

  private DelayCoverage.Source template(String id, int group) {
    var m = SpeakerModels.get(id);
    var c = console();
    double level = c == null ? 0 : c.muted(group) ? 0 : c.master() * c.fader(group);
    return new DelayCoverage.Source(
        0,
        0,
        0,
        0,
        0,
        0,
        AudioSettings.directionality ? m.hDispersion() : 360,
        AudioSettings.directionality ? m.vDispersion() : 360,
        AudioSettings.directionality ? m.rear() : 1,
        delayCurve(),
        level,
        false,
        AudioSettings.distanceAttenuation);
  }

  private DelayCoverage.Tower changeModel(DelayCoverage.Tower q, String id) {
    var m = SpeakerModels.get(id);
    var t =
        new DelayCoverage.Tower(
            q.x(),
            q.ground(),
            q.z(),
            q.height(),
            q.yaw(),
            q.modules(),
            id,
            q.group(),
            q.refX(),
            q.refY(),
            q.refZ(),
            q.delayMs(),
            q.enabled(),
            template(id, q.group()),
            m.height());
    return t.move(result.grid(), result.sources(), t.x(), t.z(), t.height(), t.modules());
  }

  private DelayCoverage.Tower copy(
      DelayCoverage.Tower q, double yaw, boolean enabled, int group, double delay) {
    return new DelayCoverage.Tower(
        q.x(),
        q.ground(),
        q.z(),
        q.height(),
        yaw,
        q.modules(),
        q.model(),
        group,
        q.refX(),
        q.refY(),
        q.refZ(),
        delay,
        enabled,
        template(q.model(), group),
        q.cabinetHeight());
  }

  private void threshold(int index, String value) {
    try {
      double d = Double.parseDouble(value.replace(',', '.'));
      if (!Double.isFinite(d) || d < 0 || d > 4) return;
      if (index == 0 && d > yellow) green = d;
      else if (index == 1 && d > orange && d < green) yellow = d;
      else if (index == 2 && d < yellow) orange = d;
      else {
        status = Tr.t("Thresholds require green > yellow > orange >= 0");
        return;
      }
      status = Tr.t("Coverage thresholds updated");
      publishGhost();
    } catch (NumberFormatException ignored) {
    }
  }

  private void invalidate() {
    generation++;
    if (future != null) future.cancel(true);
    future = null;
    sampling = null;
    stale = true;
    restore = null;
    status = Tr.t("Inputs changed: analyze again");
  }

  private DistanceCurve delayCurve() {
    var c = AudioSettings.distanceCurve;
    return new DistanceCurve(
        c.reference(),
        c.maximum() * AudioSettings.rangeFactors.getOrDefault("delay", 1.0),
        c.rolloff(),
        c.output());
  }

  private void analyze() {
    try {
      if (future != null) future.cancel(true);
      future = null;
      sampling = new PlannerSampling(zone, requestedStep, console(), mains);
      generation++;
      stale = true;
      loadedCount = sampling.loaded;
      linkedCount = console().speakers().size();
      status = Tr.t("Sampling terrain and full-block obstacles");
      page = 2;
      rebuildWidgets();
    } catch (RuntimeException e) {
      sampling = null;
      status = e.getMessage() == null ? Tr.t("Console unavailable") : e.getMessage();
    }
  }

  private void edit(UnaryOperator<DelayCoverage.Tower> change) {
    if (draft.isEmpty()) return;
    var list = new ArrayList<>(draft);
    list.set(selected, change.apply(list.get(selected)));
    draft = List.copyOf(list);
    generation++;
    if (future != null) future.cancel(true);
    future = null;
    recalcAt = Minecraft.getInstance().level.getGameTime() + 2;
    status = Tr.t("Recalculating proposed coverage");
    publishGhost();
  }

  private void align() {
    if (draft.isEmpty()) return;
    var q = draft.get(selected);
    var c = console();
    if (c != null)
      for (var p : c.speakers())
        if (Minecraft.getInstance().level.getBlockEntity(p) instanceof SpeakerBlockEntity s
            && s.group() == q.group()
            && !s.cabinets().isEmpty()) {
          var a = s.acousticAxis()[0];
          double d =
              DelayCoverage.alignment(
                  result.sources(), a.x, a.y, a.z, q.refX(), q.refY(), q.refZ());
          edit(t -> copy(t, t.yaw(), t.enabled(), t.group(), d));
          status = Tr.t("Delay computed from installed bus array at ") + p.toShortString();
          rebuildWidgets();
          return;
        }
    edit(t -> t.move(result.grid(), result.sources(), t.x(), t.z(), t.height(), t.modules()));
    status = Tr.t("Delay computed from proposed acoustic centre");
    rebuildWidgets();
  }

  private List<DelayCoverage.Tower> restored(DelayCoverage.Result r, ListTag tags) {
    var list = new ArrayList<DelayCoverage.Tower>();
    for (int i = 0; i < Math.min(3, tags.size()); i++) {
      var n = tags.getCompound(i);
      var m = SpeakerModels.get(n.getString("Model"));
      if (m == null || m.isSub()) continue;
      if (n.contains("Name")) names[list.size()] = n.getString("Name");
      double x = n.getDouble("X"),
          z = n.getDouble("Z"),
          rx = n.getDouble("RefX"),
          rz = n.getDouble("RefZ");
      int cell = r.grid().index(x, z), ref = r.grid().index(rx, rz);
      if (!r.grid().known(cell) || !r.grid().known(ref)) continue;
      double floor = r.grid().ground()[cell],
          ry = r.grid().ground()[ref] + 1.6,
          h = n.getDouble("Height"),
          d = DelayCoverage.alignment(r.sources(), x, floor + h, z, rx, ry, rz);
      if (n.contains("Delay")) d = n.getDouble("Delay");
      int group = n.getInt("Group");
      list.add(
          new DelayCoverage.Tower(
              x,
              floor,
              z,
              h,
              n.getDouble("Yaw"),
              n.getInt("Modules"),
              m.id(),
              group,
              rx,
              ry,
              rz,
              d,
              !n.contains("Enabled") || n.getBoolean("Enabled"),
              template(m.id(), group),
              m.height()));
    }
    return list;
  }

  @Override
  public void tick() {
    var mc = Minecraft.getInstance();
    if (mc.level == null || mc.player == null) return;
    if (sampling != null) {
      if (sampling.tick()) {
        var job = sampling;
        sampling = null;
        var m = SpeakerModels.get(delayModel);
        var modelTemplate =
            List.of(template(delayModel, 3), template(delayModel, 4), template(delayModel, 5));
        double weak = yellow;
        var curve = delayCurve();
        futureGeneration = generation;
        future =
            WORKER.submit(
                () ->
                    DelayCoverage.analyze(
                        job.grid, job.sources, weak, curve, m.id(), modelTemplate, m.height()));
        status = Tr.t("Solving coverage");
      } else status = Tr.t("Sampling ") + sampling.done + " / " + sampling.grid.ground().length;
    }
    if (recalcAt > 0 && mc.level.getGameTime() >= recalcAt && result != null) {
      recalcAt = 0;
      if (future != null) future.cancel(true);
      var previous = result;
      var towers = draft;
      var curve = delayCurve();
      futureGeneration = generation;
      future = WORKER.submit(() -> DelayCoverage.update(previous, towers, curve));
    }
    if (future != null && future.isDone()) {
      var completed = future;
      future = null;
      try {
        var computed = completed.get();
        if (futureGeneration == generation) {
          boolean initial = result == null || computed.grid() != result.grid();
          result = computed;
          draft = computed.towers();
          stale = false;
          selected = Math.min(selected, Math.max(0, draft.size() - 1));
          status =
              Tr.t("Ready: ")
                  + loadedCount
                  + "/"
                  + linkedCount
                  + Tr.t(" loaded arrays, grid ")
                  + result.grid().step()
                  + " m";
          if (initial && restore != null && !restore.isEmpty()) {
            var old = restored(computed, restore);
            restore = null;
            if (!old.isEmpty()) {
              draft = List.copyOf(old);
              generation++;
              recalcAt = mc.level.getGameTime() + 1;
              status = Tr.t("Restoring saved placements");
            }
          }
          publishGhost();
          if (initial) rebuildWidgets();
        }
      } catch (CancellationException ignored) {
      } catch (Exception e) {
        status = Tr.t("Analysis failed: ") + e.getClass().getSimpleName();
      }
    }
    if (pendingDelay >= 0) {
      var c = console();
      if (c != null
          && Math.abs(c.groupDelay(pendingGroup) - pendingDelay) < .05
          && c.groupLabel(pendingGroup).equals(pendingName)) {
        status = Tr.t("Server applied bus ") + (pendingGroup + 1) + ": " + pendingDelay + Tr.t(" ms");
        pendingDelay = -1;
      } else if (mc.level.getGameTime() - pendingAt > 80) {
        status = Tr.t("No confirmation: stand within 12 blocks of console");
        pendingDelay = -1;
      }
    }
  }

  private CompoundTag plan() {
    var tag = new CompoundTag();
    tag.putIntArray("Zone", zone);
    tag.putIntArray("Stage", stage);
    tag.putLongArray("Mains", mains.stream().map(BlockPos::asLong).toList());
    var list = new ListTag();
    if (!stale)
      for (int i = 0; i < draft.size(); i++) {
        var t = draft.get(i);
        var n = new CompoundTag();
        n.putString("Name", names[i]);
        n.putDouble("X", t.x());
        n.putDouble("Z", t.z());
        n.putDouble("Height", t.height());
        n.putDouble("Yaw", t.yaw());
        n.putDouble("RefX", t.refX());
        n.putDouble("RefZ", t.refZ());
        n.putString("Model", t.model());
        n.putInt("Modules", t.modules());
        n.putInt("Group", t.group());
        n.putBoolean("Enabled", t.enabled());
        if (Double.isFinite(t.delayMs()) && t.delayMs() >= 0 && t.delayMs() <= 1500)
          n.putDouble("Delay", t.delayMs());
        list.add(n);
      }
    tag.put("Towers", list);
    return tag;
  }

  private boolean near() {
    return Minecraft.getInstance().player != null
        && Minecraft.getInstance().player.distanceToSqr(consolePos.getCenter()) <= 144;
  }

  private void save() {
    if (!near()) {
      status = Tr.t("Save requires being within 12 blocks of console");
      return;
    }
    var b = AudioNet.message(AudioNet.C_DELAY_PLAN);
    b.writeBlockPos(consolePos);
    b.writeNbt(plan());
    AudioNet.toServer(b);
    status = Tr.t("Plan save requested");
  }

  private void apply() {
    if (draft.isEmpty()) return;
    var t = draft.get(selected);
    if (stale) {
      status = Tr.t("Analyze changed inputs before applying");
      return;
    }
    if (!near()
        || !t.enabled()
        || !Double.isFinite(t.delayMs())
        || t.delayMs() < 0
        || t.delayMs() > 1500) {
      status = Tr.t("Need nearby console and a 0..1500 ms delay");
      return;
    }
    var b = AudioNet.message(AudioNet.C_GROUP_CONFIG);
    b.writeBlockPos(consolePos);
    b.writeVarInt(t.group());
    b.writeFloat((float) t.delayMs());
    pendingName = names[selected].strip();
    b.writeUtf(pendingName, 32);
    AudioNet.toServer(b);
    pendingDelay = (float) t.delayMs();
    pendingGroup = t.group();
    pendingAt = Minecraft.getInstance().level.getGameTime();
    save();
    status = Tr.t("Waiting for server confirmation; whole bus is affected");
  }

  private void publishGhost() {
    if (worldGhost && result != null)
      DelayPlannerOverlay.show(
          Minecraft.getInstance().level, result, draft, stage, green, yellow, orange);
    else DelayPlannerOverlay.clear();
  }

  private int mapX() {
    return left() + 12;
  }

  private int mapY() {
    return top() + 65;
  }

  private void marker(GuiGraphics g,DelayCoverage.Grid grid,double wx,double wz,int colour,boolean cross){
    double nx=(wx-grid.x())/Math.max(1,(grid.columns()-1)*grid.step()),nz=(wz-grid.z())/Math.max(1,(grid.rows()-1)*grid.step());
    if(nx<0||nx>1||nz<0||nz>1)return;
    int x=mapX()+(int)(nx*(MAP_W-1)),y=mapY()+(int)(nz*(MAP_H-1));
    if(cross){g.fill(x-4,y,x+5,y+1,colour);g.fill(x,y-4,x+1,y+5,colour);}else g.fill(x-2,y-2,x+3,y+3,colour);
  }
  @Override
  public boolean mouseClicked(double x, double y, int button) {
    if ((page == 2 || page == 3)
        && result != null
        && button == 0
        && x >= mapX()
        && x < mapX() + MAP_W
        && y >= mapY()
        && y < mapY() + MAP_H) {
      if (page == 3 && !draft.isEmpty()) {
        var g = result.grid();
        double wx = g.x() + (x - mapX()) / MAP_W * (g.columns() - 1) * g.step(),
            wz = g.z() + (y - mapY()) / MAP_H * (g.rows() - 1) * g.step();
        int cell = g.index(wx, wz);
        if (g.known(cell)) {
          if (hasShiftDown())
            edit(t -> t.reference(result.sources(), wx, g.ground()[cell] + 1.6, wz));
          else edit(t -> t.move(g, result.sources(), wx, wz, t.height(), t.modules()));
          rebuildWidgets();
        }
      }
      return true;
    }
    return super.mouseClicked(x, y, button);
  }

  private void text(GuiGraphics g, String s, int x, int y, int colour) {
    g.drawString(
        font, font.plainSubstrByWidth(s, W - x - 12), left() + x, top() + y, colour, false);
  }

  @Override
  public void render(GuiGraphics g, int mx, int my, float partial) {
    renderBackground(g);
    Ui.panel(
        g,
        left(),
        top(),
        W,
        H,
        COLOR,
        title,
        Component.literal(font.plainSubstrByWidth(Tr.t(PAGE_HINTS[Math.max(0, Math.min(3, page))]) + "  ·  " + Tr.t("Estimated relative coverage — not measured SPL"), W - 20)));
    if (page == 0) {
      text(g, Tr.t("Stage reference X / Y / Z"), 338, 68, Ui.TEXT);
      text(g, Tr.t("Acoustic origins: selected arrays"), 338, 202, Ui.MUTED);
    }
    if (page == 1) {
      String[] labels = {Tr.t("Minimum X"), Tr.t("Minimum Z"), Tr.t("Maximum X"), Tr.t("Maximum Z"), Tr.t("Audience floor Y")};
      for (int i = 0; i < labels.length; i++) text(g, labels[i], 12, 80 + i * 31, Ui.TEXT);
      text(g, Tr.t("Unknown/unloaded cells stay grey."), 338, 160, Ui.MUTED);
      text(g, Tr.t("No chunks are forced to load."), 338, 177, Ui.MUTED);
    }
    if (page == 2 || page == 3) {
      g.fill(mapX(), mapY(), mapX() + MAP_W, mapY() + MAP_H, 0xFF171B20);
      if (result != null) {
        var grid = result.grid();
        var data = predicted ? result.predicted() : result.baseline();
        double cw = MAP_W / (double) grid.columns(), ch = MAP_H / (double) grid.rows();
        for (int i = 0; i < data.length; i++) {
          float v = data[i];
          int colour =
              !Float.isFinite(v)
                  ? 0xFF424850
                  : v >= green
                      ? 0xFF3AA66B
                      : v >= yellow ? 0xFFD2BC42 : v >= orange ? 0xFFE48636 : 0xFFB9434F;
          g.fill(
              mapX() + (int) ((i % grid.columns()) * cw),
              mapY() + (int) ((i / grid.columns()) * ch),
              mapX() + (int) ((i % grid.columns() + 1) * cw) + 1,
              mapY() + (int) ((i / grid.columns() + 1) * ch) + 1,
              colour);
        }
        for (int i = 0; i < draft.size(); i++) {
          var t = draft.get(i);
          int
              x =
                  mapX()
                      + (int)
                          ((t.x() - grid.x())
                              / Math.max(1, (grid.columns() - 1) * grid.step())
                              * MAP_W),
              z =
                  mapY()
                      + (int)
                          ((t.z() - grid.z())
                              / Math.max(1, (grid.rows() - 1) * grid.step())
                              * MAP_H);
          g.fill(x - 4, z - 4, x + 4, z + 4, !t.enabled() ? 0xFF777777 : i == selected ? 0xFFFFFFFF : 0xFF53B7FF);
          g.drawString(font, "" + (i + 1), x + 5, z - 3, Ui.TEXT, false);
        }
        for(var source:result.sources())if(source.main())marker(g,grid,source.x(),source.z(),0xFF66D9FF,false);
        if(!draft.isEmpty()){var t=draft.get(selected);marker(g,grid,t.refX(),t.refZ(),0xFFFFD66B,true);}
      }
      if (page == 2) {
        text(g, Tr.t("Green / yellow / orange thresholds"), 338, 137, Ui.MUTED);
        text(g, Tr.t("Red: below orange; grey: unknown."), 338, 181, Ui.MUTED);
        text(g, Tr.t("Gold cross: selected alignment point"), 338, 194, Ui.MUTED);
      } else if (!draft.isEmpty()) {
        var t = draft.get(selected);
        text(g, Tr.t("Bus label"), 12, 286, Ui.TEXT);
        text(g, Tr.t("Map: move / Shift-click: reference"), 338, 115, Ui.MUTED);
        text(g, Tr.t("Manual milliseconds:"), 338, 215, Ui.MUTED);
      }
    }
    text(g, status, 355, H - 19, Ui.MUTED);
    super.render(g, mx, my, partial);
  }

  @Override
  public void removed() {
    if (future != null) future.cancel(true);
    sampling = null;
    super.removed();
  }

  @Override
  public void onClose() {
    if (future != null) future.cancel(true);
    sampling = null;
    Minecraft.getInstance().setScreen(parent);
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }
}
