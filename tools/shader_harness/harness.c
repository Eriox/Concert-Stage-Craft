/*
 * CSC shader harness (1.2.0 rev. 16): compiles and runs the real CSC core shaders (assets/minecraft/shaders/core) on an offscreen
 * OpenGL 3.3 core context (Mesa llvmpipe through EGL, no window, no Minecraft), with the uniforms and textures of a test scene.
 * It checks that the GLSL the mod ships compiles and links, and gives the exact output of the shaders for a scene (the pictures of
 * tools/shader_harness/scene.py). It is NOT Minecraft: no world, no game passes, no Shimmer.
 *
 * Usage: harness <vsh> <fsh> <include dir> <uniform file> <width> <height> <out.raw float RGBA>
 * Uniform file, one per line:
 *   f <name> <1..4 floats>          vec / float
 *   m <name> <16 floats>            mat4, column major
 *   t <name> <unit> <raw file> <w> <h> <r32f|rgba8>
 *   q <x0> <y0> <x1> <y1>           the quad drawn (Position, NDC when the vertex shader passes it through), default full screen
 * Build: gcc -O2 -o harness harness.c -ldl
 */
#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

typedef void *EGLDisplay, *EGLConfig, *EGLContext, *EGLSurface;
typedef int EGLint;
typedef unsigned EGLBoolean;
typedef unsigned GLuint, GLenum, GLbitfield;
typedef int GLint, GLsizei;
typedef float GLfloat;
typedef char GLchar;
typedef long GLsizeiptr;

#define GL_FRAGMENT_SHADER 0x8B30
#define GL_VERTEX_SHADER 0x8B31
#define GL_COMPILE_STATUS 0x8B81
#define GL_LINK_STATUS 0x8B82
#define GL_TEXTURE_2D 0x0DE1
#define GL_TEXTURE0 0x84C0
#define GL_R32F 0x822E
#define GL_RED 0x1903
#define GL_RGBA 0x1908
#define GL_RGBA8 0x8058
#define GL_RGBA32F 0x8814
#define GL_FLOAT 0x1406
#define GL_UNSIGNED_BYTE 0x1401
#define GL_TEXTURE_MIN_FILTER 0x2801
#define GL_TEXTURE_MAG_FILTER 0x2800
#define GL_TEXTURE_WRAP_S 0x2802
#define GL_TEXTURE_WRAP_T 0x2803
#define GL_LINEAR 0x2601
#define GL_NEAREST 0x2600
#define GL_CLAMP_TO_EDGE 0x812F
#define GL_FRAMEBUFFER 0x8D40
#define GL_COLOR_ATTACHMENT0 0x8CE0
#define GL_FRAMEBUFFER_COMPLETE 0x8CD5
#define GL_ARRAY_BUFFER 0x8892
#define GL_STATIC_DRAW 0x88E4
#define GL_TRIANGLES 0x0004
#define GL_COLOR_BUFFER_BIT 0x4000

static void *(*gpa)(const char *);
#define F(ret, name, args) static ret(*name) args;
F(GLuint, glCreateShader, (GLenum))
F(void, glShaderSource, (GLuint, GLsizei, const GLchar **, const GLint *))
F(void, glCompileShader, (GLuint))
F(void, glGetShaderiv, (GLuint, GLenum, GLint *))
F(void, glGetShaderInfoLog, (GLuint, GLsizei, GLsizei *, GLchar *))
F(GLuint, glCreateProgram, (void))
F(void, glAttachShader, (GLuint, GLuint))
F(void, glBindAttribLocation, (GLuint, GLuint, const GLchar *))
F(void, glLinkProgram, (GLuint))
F(void, glGetProgramiv, (GLuint, GLenum, GLint *))
F(void, glGetProgramInfoLog, (GLuint, GLsizei, GLsizei *, GLchar *))
F(void, glUseProgram, (GLuint))
F(GLint, glGetUniformLocation, (GLuint, const GLchar *))
F(void, glUniform1f, (GLint, GLfloat))
F(void, glUniform2f, (GLint, GLfloat, GLfloat))
F(void, glUniform3f, (GLint, GLfloat, GLfloat, GLfloat))
F(void, glUniform4f, (GLint, GLfloat, GLfloat, GLfloat, GLfloat))
F(void, glUniform1i, (GLint, GLint))
F(void, glUniformMatrix4fv, (GLint, GLsizei, unsigned char, const GLfloat *))
F(void, glGenTextures, (GLsizei, GLuint *))
F(void, glBindTexture, (GLenum, GLuint))
F(void, glActiveTexture, (GLenum))
F(void, glTexImage2D, (GLenum, GLint, GLint, GLsizei, GLsizei, GLint, GLenum, GLenum, const void *))
F(void, glTexParameteri, (GLenum, GLenum, GLint))
F(void, glGenerateMipmap, (GLenum))
F(void, glGenFramebuffers, (GLsizei, GLuint *))
F(void, glBindFramebuffer, (GLenum, GLuint))
F(void, glFramebufferTexture2D, (GLenum, GLenum, GLenum, GLuint, GLint))
F(GLenum, glCheckFramebufferStatus, (GLenum))
F(void, glViewport, (GLint, GLint, GLsizei, GLsizei))
F(void, glClearColor, (GLfloat, GLfloat, GLfloat, GLfloat))
F(void, glClear, (GLbitfield))
F(void, glGenVertexArrays, (GLsizei, GLuint *))
F(void, glBindVertexArray, (GLuint))
F(void, glGenBuffers, (GLsizei, GLuint *))
F(void, glBindBuffer, (GLenum, GLuint))
F(void, glBufferData, (GLenum, GLsizeiptr, const void *, GLenum))
F(void, glEnableVertexAttribArray, (GLuint))
F(void, glVertexAttribPointer, (GLuint, GLint, GLenum, unsigned char, GLsizei, const void *))
F(void, glDrawArrays, (GLenum, GLint, GLsizei))
F(void, glReadPixels, (GLint, GLint, GLsizei, GLsizei, GLenum, GLenum, void *))
F(void, glFinish, (void))
#define L(name) name = gpa(#name); if (!name) { fprintf(stderr, "missing %s\n", #name); return 2; }

static char *slurp(const char *path) {
    FILE *f = fopen(path, "rb");
    if (!f) { fprintf(stderr, "cannot read %s\n", path); exit(2); }
    fseek(f, 0, SEEK_END); long n = ftell(f); fseek(f, 0, SEEK_SET);
    char *b = malloc(n + 1); fread(b, 1, n, f); b[n] = 0; fclose(f);
    return b;
}

/* #moj_import <name> -> the file from the include folder (what Minecraft's GlslPreprocessor does for core shaders) */
static char *preprocess(const char *src, const char *inc) {
    size_t cap = strlen(src) * 4 + 65536, len = 0;
    char *out = malloc(cap);
    const char *p = src;
    while (*p) {
        const char *e = strchr(p, '\n'); if (!e) e = p + strlen(p);
        const char *s = p; while (s < e && (*s == ' ' || *s == '\t')) s++;
        if (!strncmp(s, "#moj_import", 11)) {
            const char *a = strchr(s, '<'), *b = a ? strchr(a, '>') : NULL;
            if (!a || !b || b > e) { fprintf(stderr, "bad #moj_import line\n"); exit(2); }
            char path[1024]; snprintf(path, sizeof path, "%s/%.*s", inc, (int)(b - a - 1), a + 1);
            char *t = slurp(path); size_t tl = strlen(t);
            if (len + tl + 2 > cap) { cap = (len + tl) * 2; out = realloc(out, cap); }
            memcpy(out + len, t, tl); len += tl; out[len++] = '\n'; free(t);
        } else {
            size_t l = e - p;
            if (len + l + 2 > cap) { cap = (len + l) * 2; out = realloc(out, cap); }
            memcpy(out + len, p, l); len += l; out[len++] = '\n';
        }
        p = *e ? e + 1 : e;
    }
    out[len] = 0;
    return out;
}

static GLuint compile(GLenum type, const char *src, const char *name) {
    GLuint s = glCreateShader(type);
    glShaderSource(s, 1, &src, NULL);
    glCompileShader(s);
    GLint ok; glGetShaderiv(s, GL_COMPILE_STATUS, &ok);
    char log[8192]; GLsizei n = 0; glGetShaderInfoLog(s, sizeof log, &n, log);
    if (!ok) { fprintf(stderr, "COMPILE FAILED %s\n%s\n", name, log); exit(1); }
    if (n > 0) fprintf(stderr, "compile log %s: %s\n", name, log);
    return s;
}

int main(int argc, char **argv) {
    if (argc < 8) { fprintf(stderr, "usage: harness vsh fsh incdir uniforms w h out.raw\n"); return 2; }
    int W = atoi(argv[5]), H = atoi(argv[6]);
    void *h = dlopen("libEGL.so.1", RTLD_NOW);
    if (!h) { fprintf(stderr, "no libEGL\n"); return 2; }
    EGLDisplay (*getDisplay)(void *) = dlsym(h, "eglGetDisplay");
    EGLBoolean (*init)(EGLDisplay, EGLint *, EGLint *) = dlsym(h, "eglInitialize");
    EGLBoolean (*bindApi)(unsigned) = dlsym(h, "eglBindAPI");
    EGLContext (*mkctx)(EGLDisplay, EGLConfig, EGLContext, const EGLint *) = dlsym(h, "eglCreateContext");
    EGLBoolean (*cur)(EGLDisplay, EGLSurface, EGLSurface, EGLContext) = dlsym(h, "eglMakeCurrent");
    gpa = dlsym(h, "eglGetProcAddress");
    EGLDisplay d = getDisplay(0); EGLint ma, mi;
    if (!init(d, &ma, &mi)) { fprintf(stderr, "eglInitialize failed\n"); return 2; }
    bindApi(0x30A2);
    EGLint ca[] = {0x3098, 3, 0x30FB, 3, 0x30FD, 1, 0x3038};       /* 3.3 core */
    EGLContext x = mkctx(d, 0, 0, ca);
    if (!x || !cur(d, 0, 0, x)) { fprintf(stderr, "no GL context\n"); return 2; }
    L(glCreateShader) L(glShaderSource) L(glCompileShader) L(glGetShaderiv) L(glGetShaderInfoLog) L(glCreateProgram) L(glAttachShader)
    L(glBindAttribLocation) L(glLinkProgram) L(glGetProgramiv) L(glGetProgramInfoLog) L(glUseProgram) L(glGetUniformLocation)
    L(glUniform1f) L(glUniform2f) L(glUniform3f) L(glUniform4f) L(glUniform1i) L(glUniformMatrix4fv) L(glGenTextures) L(glBindTexture)
    L(glActiveTexture) L(glTexImage2D) L(glTexParameteri) L(glGenerateMipmap) L(glGenFramebuffers) L(glBindFramebuffer) L(glFramebufferTexture2D)
    L(glCheckFramebufferStatus) L(glViewport) L(glClearColor) L(glClear) L(glGenVertexArrays) L(glBindVertexArray) L(glGenBuffers)
    L(glBindBuffer) L(glBufferData) L(glEnableVertexAttribArray) L(glVertexAttribPointer) L(glDrawArrays) L(glReadPixels) L(glFinish)

    char *vs = preprocess(slurp(argv[1]), argv[3]), *fs = preprocess(slurp(argv[2]), argv[3]);
    GLuint prog = glCreateProgram();
    glAttachShader(prog, compile(GL_VERTEX_SHADER, vs, argv[1]));
    glAttachShader(prog, compile(GL_FRAGMENT_SHADER, fs, argv[2]));
    glBindAttribLocation(prog, 0, "Position");
    glLinkProgram(prog);
    GLint ok; glGetProgramiv(prog, GL_LINK_STATUS, &ok);
    if (!ok) { char log[8192]; glGetProgramInfoLog(prog, sizeof log, NULL, log); fprintf(stderr, "LINK FAILED\n%s\n", log); return 1; }
    glUseProgram(prog);

    float quad[4] = {-1, -1, 1, 1};
    FILE *u = fopen(argv[4], "r");
    if (!u) { fprintf(stderr, "no uniform file\n"); return 2; }
    char line[4096];
    while (fgets(line, sizeof line, u)) {
        char kind[8], name[128];
        if (sscanf(line, "%7s", kind) != 1 || kind[0] == '#') continue;
        if (kind[0] == 'q') { sscanf(line, "q %f %f %f %f", &quad[0], &quad[1], &quad[2], &quad[3]); continue; }
        if (sscanf(line, "%7s %127s", kind, name) != 2) continue;
        GLint loc = glGetUniformLocation(prog, name);
        char *rest = strstr(line, name) + strlen(name);
        if (kind[0] == 'f') {
            float v[4] = {0}; int n = 0; char *q = rest, *e;
            while (n < 4) { float f = strtof(q, &e); if (e == q) break; v[n++] = f; q = e; }
            if (loc < 0) continue;                                   /* optimised out or not declared: as Minecraft's safeGetUniform */
            if (n == 1) glUniform1f(loc, v[0]); else if (n == 2) glUniform2f(loc, v[0], v[1]);
            else if (n == 3) glUniform3f(loc, v[0], v[1], v[2]); else glUniform4f(loc, v[0], v[1], v[2], v[3]);
        } else if (kind[0] == 'm') {
            float m[16]; char *q = rest, *e;
            for (int i = 0; i < 16; i++) { m[i] = strtof(q, &e); q = e; }
            if (loc >= 0) glUniformMatrix4fv(loc, 1, 0, m);
        } else if (kind[0] == 't') {
            int unit, tw, th; char path[1024], fmt[16];
            sscanf(rest, "%d %1023s %d %d %15s", &unit, path, &tw, &th, fmt);
            FILE *tf = fopen(path, "rb");
            if (!tf) { fprintf(stderr, "no texture %s\n", path); return 2; }
            int mip = !strcmp(fmt, "rgba8mip");                /* rev. 16g: gobo with mipmaps, as the mod makes them */
            int rgba = mip || !strcmp(fmt, "rgba8");
            size_t bytes = (size_t)tw * th * (rgba ? 4 : 4);
            void *data = malloc(bytes); fread(data, 1, bytes, tf); fclose(tf);
            GLuint t; glGenTextures(1, &t);
            glActiveTexture(GL_TEXTURE0 + unit); glBindTexture(GL_TEXTURE_2D, t);
            if (rgba) glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, tw, th, 0, GL_RGBA, GL_UNSIGNED_BYTE, data);
            else glTexImage2D(GL_TEXTURE_2D, 0, GL_R32F, tw, th, 0, GL_RED, GL_FLOAT, data);
            /* the mod: gobo / pattern textures linear (setFilter(true, false)), depth copies nearest */
            int lin = rgba;
            if (mip) glGenerateMipmap(GL_TEXTURE_2D);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, mip ? 0x2703 /* LINEAR_MIPMAP_LINEAR */ : lin ? GL_LINEAR : GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, lin ? GL_LINEAR : GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            if (loc >= 0) glUniform1i(loc, unit);
        }
    }
    fclose(u);

    /* the target on its own unit: binding it on the active unit would replace the last texture of the uniform file */
    GLuint ct, fb; glActiveTexture(GL_TEXTURE0 + 15); glGenTextures(1, &ct); glBindTexture(GL_TEXTURE_2D, ct);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA32F, W, H, 0, GL_RGBA, GL_FLOAT, NULL);
    glGenFramebuffers(1, &fb); glBindFramebuffer(GL_FRAMEBUFFER, fb);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, ct, 0);
    if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) { fprintf(stderr, "fbo incomplete\n"); return 2; }
    glViewport(0, 0, W, H);
    glClearColor(0, 0, 0, 0); glClear(GL_COLOR_BUFFER_BIT);
    float v[18] = {quad[0], quad[1], 0, quad[2], quad[1], 0, quad[2], quad[3], 0, quad[0], quad[1], 0, quad[2], quad[3], 0, quad[0], quad[3], 0};
    GLuint vao, vbo; glGenVertexArrays(1, &vao); glBindVertexArray(vao);
    glGenBuffers(1, &vbo); glBindBuffer(GL_ARRAY_BUFFER, vbo); glBufferData(GL_ARRAY_BUFFER, sizeof v, v, GL_STATIC_DRAW);
    glEnableVertexAttribArray(0); glVertexAttribPointer(0, 3, GL_FLOAT, 0, 0, 0);
    glDrawArrays(GL_TRIANGLES, 0, 6);
    glFinish();
    /* HARNESS_REPEAT=n: draw n more times and report the mean time of one draw (relative costs only: llvmpipe is a CPU rasterizer) */
    const char *rep = getenv("HARNESS_REPEAT");
    if (rep && atoi(rep) > 0) {
        struct timespec a, b; int n = atoi(rep);
        clock_gettime(CLOCK_MONOTONIC, &a);
        for (int i = 0; i < n; i++) glDrawArrays(GL_TRIANGLES, 0, 6);
        glFinish();
        clock_gettime(CLOCK_MONOTONIC, &b);
        fprintf(stderr, "draw %.3f ms\n", ((b.tv_sec - a.tv_sec) * 1e3 + (b.tv_nsec - a.tv_nsec) / 1e6) / n);
    }
    float *px = malloc((size_t)W * H * 16);
    glReadPixels(0, 0, W, H, GL_RGBA, GL_FLOAT, px);
    FILE *o = fopen(argv[7], "wb"); fwrite(px, 16, (size_t)W * H, o); fclose(o);
    fprintf(stderr, "ok %s + %s -> %s (%dx%d)\n", argv[1], argv[2], argv[7], W, H);
    return 0;
}
