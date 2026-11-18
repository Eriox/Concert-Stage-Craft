package com.concertstage.stage;
import com.concertstage.StageContent;
import java.util.*;
import net.minecraft.world.item.Item;
/** Creative tabs, one per family (trusses, towers and rigging, platforms, CSC V3 scaffolding, curtains, tools).
 * Inside a tab the order follows actual use. Technical save aliases stay registered but hidden. */
public final class StageCatalog {
 private StageCatalog(){}
 /** Creative tabs, in display order. {@code id} is the registry path, {@code icon} an item id. */
 public enum Tab{
  TRUSSES("trusses","truss_h1_l2"),RIGGING("rigging","motor_tower_10_corner3"),STAGE("stage","stage_deck_2x1_h040"),REGIE("regie","regie_noeud_t"),
  CURTAINS("curtains","curtain_rail"),TOOLS("tools","lift_remote"),AUDIO("audio","csc_veyra_a1"),LIGHTING("lighting","csc_vortex_x1"),
  LIVEVISION("livevision","csc_led_panel_p1");
  public final String id,icon;
  Tab(String id,String icon){this.id=id;this.icon=icon;}
 }
 /** Sound system (1.1.0): speakers (Veyra, Orvane, Noryx, AUREX and their flying frames), console and their tools. */
 static boolean isAudio(String id){return id.startsWith("csc_aurex_")||id.startsWith("csc_veyra_")||id.startsWith("csc_orvane_")||id.equals("csc_delay_planner")||id.startsWith("csc_noryx_")||id.equals("csc_audio_console")||id.equals("csc_array_tool")||id.equals("csc_audio_link_tool");}
 /** Lighting (1.2.0): the four CSC fixtures, the LUMICORE X1 desk and the patch tool. */
 static boolean isLighting(String id){return com.concertstage.light.core.FixtureProfile.get(id)!=null||id.equals("csc_vortex_x1")||id.equals("csc_lumora_w7")||id.equals("csc_lumora_w19")||id.equals("csc_pulsestrike_m")||id.equals("csc_lumicore_x1")||id.equals("csc_artnet_node")||id.equals("csc_config_card")||id.equals("csc_focus_tool")||id.equals("csc_light_patch_tool")||id.equals("csc_fixture_angle_tool");}
 /** LiveVision (1.3.0): LED panels and their accessories, the VP1 processor, the four cameras. */
 static boolean isVision(String id){return id.startsWith("csc_led_")||id.equals("csc_vision_processor_vp1")||id.equals("csc_cam_ptz_p1")||id.equals("csc_broadcast_b1")||id.equals("csc_crane_c1")||id.equals("csc_remote_r1")||id.equals("csc_camera_tool");}
 public static Tab tabOf(String id){
  if(isVision(id))return Tab.LIVEVISION;
  if(isLighting(id))return Tab.LIGHTING;
  if(isAudio(id))return Tab.AUDIO;
  if(id.startsWith("truss_")||id.equals("ring_truss")||id.startsWith("curved_"))return Tab.TRUSSES;
  if(id.startsWith("motor_tower")||id.equals("palan_chaine"))return Tab.RIGGING;
  if(id.startsWith("csc_"))return Tab.STAGE;          // CSC risers, DJ / FOH / technical platforms, festival stages
  if(id.endsWith("_tool")||id.endsWith("_remote"))return Tab.TOOLS;
  if(id.startsWith("curtain_")||id.startsWith("stage_tarp")||id.startsWith("regie_tarp"))return Tab.CURTAINS;
  if(id.startsWith("regie_"))return Tab.REGIE;
  return Tab.STAGE;
 }
 private static int section(String id){
  if(id.startsWith("truss_")||id.equals("ring_truss")||id.startsWith("curved_"))return 0;
  if(id.startsWith("motor_tower"))return 1;
  if(id.equals("palan_chaine"))return 2;
  StageSpec spec=StageSpec.find(id);
  if(id.startsWith("csc_festival_"))return 6;
  if(id.startsWith("csc_")&&spec!=null)return 7;
  if(spec!=null)return switch(spec.kind){case "platform"->3;case "riser"->4;case "stairs","railing"->5;case "support","extension","assembly"->6;default->7;};
  if(id.endsWith("_tool")||id.endsWith("_remote"))return 8;
  if(id.startsWith("curtain_"))return 7;
  if(id.startsWith("stage_tarp")||id.startsWith("regie_tarp"))return 7;
  if(id.startsWith("regie_"))return 7;
  return 8;
 }
 private static List<Map.Entry<String,Item>> entries(){Map<String,Integer> order=new LinkedHashMap<>();for(String key:StageContent.ITEMS.keySet())order.computeIfAbsent(key.replaceFirst("_noir$",""),k->order.size());return StageContent.ITEMS.entrySet().stream()
  .filter(e->!Set.of("stage_riser","stage_stairs","raised_stage_platform_2x1_h120","assembly_festival_stage_8x4_h120").contains(e.getKey()))
  .sorted(Comparator.comparingInt((Map.Entry<String,Item> e)->section(e.getKey())).thenComparingInt(e->order.get(e.getKey().replaceFirst("_noir$",""))))
  .toList();}
 /** Every visible item, all tabs together (kept for compatibility). */
 public static List<Item> creativeItems(){return entries().stream().map(Map.Entry::getValue).toList();}
 public static List<Item> items(Tab tab){return entries().stream().filter(e->tabOf(e.getKey())==tab).map(Map.Entry::getValue).toList();}
 public static Item icon(Tab tab){Item i=StageContent.ITEMS.get(tab.icon);return i!=null?i:icon();}
 public static Item icon(){return StageContent.ITEMS.get("stage_deck_2x1_h040");}
}
