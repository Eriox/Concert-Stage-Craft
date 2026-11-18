package org.joml; public class Matrix4f implements Matrix4fc { public Matrix4f(){} public Matrix4f(Matrix4fc m){}
 public Vector3f transformPosition(Vector3f v){return v;} public Vector3f transformDirection(Vector3f v){return v;}
 public Matrix4f invert(){return this;} public Matrix4f mul(Matrix4fc m){return this;} public Vector4f transform(Vector4f v){return v;}
 public float m00(){return 0;} public float m01(){return 0;} public float m02(){return 0;} public float m10(){return 0;} public float m11(){return 0;} public float m12(){return 0;} public float m20(){return 0;} public float m21(){return 0;} public float m22(){return 0;} public float m30(){return 0;} public float m31(){return 0;} public float m32(){return 0;}
 public Matrix4f translate(float a,float b,float c){return this;} public Matrix4f set(Matrix4fc m){return this;} public Matrix4f identity(){return this;}
 public Matrix4f rotate(Quaternionfc q){return this;} public Matrix4f scale(float a,float b,float c){return this;}
 public Vector3f getTranslation(Vector3f v){return v;} public Matrix4f setTranslation(float a,float b,float c){return this;}
 public Matrix4f m30(float v){return this;} public Matrix4f m31(float v){return this;} public Matrix4f m32(float v){return this;} public Matrix4f lookAt(float a,float b,float c,float d,float e,float f,float g,float h,float i){return this;}
 public Matrix4f perspective(float a,float b,float c,float d){return this;}}
