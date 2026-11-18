package com.concertstage.block;

/** Implemented by the block entities of curtains and tarps so the Curtain Ruler can resize them. */
public interface ResizableCloth {
    /** mode 0 = length, mode 1 = height (curtain) / width (tarp). */
    void adjust(int mode, int delta);

    int getLength();

    int getHeight();
}
