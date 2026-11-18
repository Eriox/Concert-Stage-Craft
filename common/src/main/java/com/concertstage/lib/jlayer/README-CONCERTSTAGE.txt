JLayer MP3 decoder (javazoom.jl.decoder), LGPL 2.1 - see LICENSE-LGPL-2.1.txt.
Source: https://github.com/umjammer/jlayer (maintained fork of JavaZOOM JLayer 1.0.1), version 1.0.6.
Changes made for Concert Stage Craft: the package was renamed from javazoom.jl.decoder to com.concertstage.lib.jlayer (so that it
can never clash with another mod that carries JLayer), and the Decoder.convertToWav helper (which needs the converter package) was
removed. Nothing else was changed. Its data tables (*.ser) are in resources/com/concertstage/lib/jlayer.
