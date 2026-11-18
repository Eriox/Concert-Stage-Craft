package com.concertstage.audio.core;

/** Fractional mono delay on the shared sample clock; tap changes blend over 20 ms. */
public final class GroupDelayLine {
  /** 1.1.1 audio fix: allocated the first time a delay is asked for (a slot with no delay costs no memory: 288 KB each). */
  private float[] ring;
  private final int size;
  private final int rate;
  private int write, fade;
  private double oldTap, tap;
  private boolean ready;

  public GroupDelayLine(int rate) {
    if (rate <= 0) throw new IllegalArgumentException("rate");
    this.rate = rate;
    size = (int) (rate * 1.5) + 3;
  }

  private double read(double delay) {
    double p = write - delay;
    int i = (int) Math.floor(p);
    double f = p - i;
    int a = Math.floorMod(i, ring.length);
    return ring[a] * (1 - f) + ring[(a + 1) % ring.length] * f;
  }

  /** 16-bit feed (kept for the tools): the delayed samples are rounded back. */
  public void process(short[] data, int frames, float milliseconds) {
    if (scratch.length < frames) scratch = new float[frames];
    for (int i = 0; i < frames; i++) scratch[i] = data[i];
    process(scratch, frames, milliseconds);
    for (int i = 0; i < frames; i++) data[i] = (short) Math.round(Math.max(-32768, Math.min(32767, scratch[i])));
  }

  private float[] scratch = new float[0];

  /** 1.1.1: forget the past samples (a group that starts again after being silent). */
  public void clear() {
    if (ring != null) java.util.Arrays.fill(ring, 0f);
    fade = 0;
  }

  /** 1.1.1: float feed (the mixer: no 16-bit rounding or clipping between the group filters and the group gain). */
  public void process(float[] data, int frames, float milliseconds) {
    double target =
        Float.isFinite(milliseconds)
            ? Math.max(0, Math.min(1500, milliseconds)) * rate / 1000.0
            : 0;
    if (ring == null) {
      if (target == 0) { tap = oldTap = 0; ready = true; return; }      // no delay so far: the samples pass as they are
      ring = new float[size];
    }
    int duration = Math.max(1, rate / 50);
    if (!ready) {
      tap = oldTap = target;
      ready = true;
    } else if (target != tap) {
      oldTap = tap;
      tap = target;
      fade = duration;
    }
    for (int i = 0; i < frames; i++) {
      ring[write] = data[i];
      double v = read(tap);
      if (fade > 0) {
        double t = 1 - fade / (double) duration;
        v = read(oldTap) * (1 - t) + v * t;
        fade--;
      }
      data[i] = (float) v;
      write = (write + 1) % ring.length;
    }
  }
}
