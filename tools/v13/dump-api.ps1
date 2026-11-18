# CSC 1.3.0 - writes the signatures of the game classes used by LiveVision (javap of the mapped Forge jar) to tools\v13\api.txt
$ErrorActionPreference = 'Continue'
$out = Join-Path $PSScriptRoot 'api.txt'
$jar = Get-ChildItem -Path (Join-Path $env:USERPROFILE '.gradle\caches') -Recurse -Filter 'forge-1.20.1-*.jar' -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notmatch 'sources|universal|installer|userdev|slim|srg|extra|client' } | Sort-Object Length -Descending | Select-Object -First 1
if (-not $jar) { "no mapped forge jar found" | Out-File $out; exit }
"JAR $($jar.FullName)" | Out-File $out -Encoding utf8
$classes = @(
 'net.minecraft.client.renderer.LevelRenderer', 'net.minecraft.client.renderer.ViewArea',
 'net.minecraft.client.renderer.chunk.ChunkRenderDispatcher$RenderChunk', 'net.minecraft.client.renderer.chunk.ChunkRenderDispatcher$CompiledChunk',
 'net.minecraft.client.Camera', 'net.minecraft.client.renderer.culling.Frustum', 'net.minecraft.client.renderer.FogRenderer',
 'net.minecraft.client.renderer.GameRenderer', 'com.mojang.blaze3d.pipeline.RenderTarget', 'com.mojang.blaze3d.pipeline.TextureTarget',
 'com.mojang.blaze3d.platform.NativeImage', 'net.minecraft.client.renderer.texture.DynamicTexture', 'net.minecraft.client.renderer.texture.AbstractTexture',
 'com.mojang.blaze3d.vertex.VertexBuffer', 'net.minecraft.client.renderer.ShaderInstance', 'net.minecraft.client.renderer.entity.EntityRenderDispatcher',
 'net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher', 'net.minecraft.client.renderer.RenderBuffers', 'net.minecraft.client.Minecraft',
 'net.minecraft.client.gui.components.AbstractSliderButton', 'net.minecraft.client.gui.components.CycleButton', 'net.minecraft.client.gui.GuiGraphics',
 'net.minecraft.client.renderer.RenderType', 'com.mojang.blaze3d.systems.RenderSystem', 'net.minecraft.client.multiplayer.ClientLevel')
foreach ($c in $classes) {
    "===== $c" | Out-File $out -Append -Encoding utf8
    & javap -p -cp $jar.FullName $c 2>&1 | Out-File $out -Append -Encoding utf8
}
