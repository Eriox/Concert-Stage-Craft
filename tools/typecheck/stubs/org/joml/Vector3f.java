package org.joml;
public class Vector3f implements Vector3fc {
  public float x,y,z;
  public Vector3f(){} public Vector3f(float x,float y,float z){this.x=x;this.y=y;this.z=z;} public Vector3f(Vector3fc v){}
  public Vector3f set(float x,float y,float z){return this;} public Vector3f set(Vector3fc v){return this;}
  public Vector3f mul(float s){return this;} public Vector3f mul(float a,float b,float c){return this;}
  public Vector3f add(Vector3fc v){return this;} public Vector3f add(float a,float b,float c){return this;}
  public Vector3f sub(Vector3fc v){return this;} public Vector3f sub(float a,float b,float c){return this;}
  public Vector3f cross(Vector3fc v){return this;} public Vector3f cross(float a,float b,float c){return this;}
  public Vector3f normalize(){return this;} public float dot(Vector3fc v){return 0;} public float length(){return 0;} public float lengthSquared(){return 0;} public float distance(Vector3fc v){return 0;}
  public Vector3f div(float s){return this;} public Vector3f rotate(Quaternionfc q){return this;} public Vector3f negate(){return this;}
  public float x(){return x;} public float y(){return y;} public float z(){return z;}
  public Vector3f transform(Matrix3fc m){return this;} public Vector3f mulPosition(Matrix4fc m){return this;}
}
