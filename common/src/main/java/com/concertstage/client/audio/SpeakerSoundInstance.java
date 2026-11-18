package com.concertstage.client.audio;

import com.concertstage.StageContent;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;

/** Listener-relative stereo console stream. PCM contains array gain, directivity and pan;
 * Minecraft still applies its master/category controls. */
public class SpeakerSoundInstance extends AbstractTickableSoundInstance {
    private final ClientAudio.Voice voice;

    SpeakerSoundInstance(ClientAudio.Voice voice) {
        super(StageContent.SPEAKER_STREAM, SoundSource.RECORDS, SoundInstance.createUnseededRandom());
        this.voice = voice;
        this.attenuation = Attenuation.NONE;
        this.looping = false;
        this.delay = 0;
        this.relative = true;
        this.volume = 1;
        this.x = 0;
        this.y = 0;
        this.z = 0;
    }

    @Override
    public void tick() {
        if (voice.dead) { stop(); return; }
        volume = 1;
        x = y = z = 0;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    private CompletableFuture<AudioStream> stream() {
        return CompletableFuture.supplyAsync(voice::open, Util.backgroundExecutor());
    }

    /** Forge: the stream of this sound (Forge's SoundInstance#getStream). */
    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary soundBuffers, Sound sound, boolean looping) {
        return stream();
    }

    /** Fabric: the stream of this sound (Fabric API's FabricSoundInstance#getAudioStream). */
    public CompletableFuture<AudioStream> getAudioStream(SoundBufferLibrary loader, ResourceLocation id, boolean repeatInstantly) {
        return stream();
    }
}
