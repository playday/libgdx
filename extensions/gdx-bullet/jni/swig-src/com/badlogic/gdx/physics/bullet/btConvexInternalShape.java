/* ----------------------------------------------------------------------------
 * This file was automatically generated by SWIG (http://www.swig.org).
 * Version 2.0.5
 *
 * Do not make changes to this file unless you know what you are doing--modify
 * the SWIG interface file instead.
 * ----------------------------------------------------------------------------- */

package com.badlogic.gdx.physics.bullet;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Matrix3;

public class btConvexInternalShape extends btConvexShape {
  private long swigCPtr;

  protected btConvexInternalShape(long cPtr, boolean cMemoryOwn) {
    super(gdxBulletJNI.btConvexInternalShape_SWIGUpcast(cPtr), cMemoryOwn);
    swigCPtr = cPtr;
  }

  public static long getCPtr(btConvexInternalShape obj) {
    return (obj == null) ? 0 : obj.swigCPtr;
  }

  protected void finalize() {
    delete();
  }

  public synchronized void delete() {
    if (swigCPtr != 0) {
      if (swigCMemOwn) {
        swigCMemOwn = false;
        gdxBulletJNI.delete_btConvexInternalShape(swigCPtr);
      }
      swigCPtr = 0;
    }
    super.delete();
  }

  public Vector3 getImplicitShapeDimensions() {
	return gdxBulletJNI.btConvexInternalShape_getImplicitShapeDimensions(swigCPtr, this);
}

  public void setImplicitShapeDimensions(Vector3 dimensions) {
    gdxBulletJNI.btConvexInternalShape_setImplicitShapeDimensions(swigCPtr, this, dimensions);
  }

  public void setSafeMargin(float minDimension, float defaultMarginMultiplier) {
    gdxBulletJNI.btConvexInternalShape_setSafeMargin__SWIG_0(swigCPtr, this, minDimension, defaultMarginMultiplier);
  }

  public void setSafeMargin(float minDimension) {
    gdxBulletJNI.btConvexInternalShape_setSafeMargin__SWIG_1(swigCPtr, this, minDimension);
  }

  public void setSafeMargin(Vector3 halfExtents, float defaultMarginMultiplier) {
    gdxBulletJNI.btConvexInternalShape_setSafeMargin__SWIG_2(swigCPtr, this, halfExtents, defaultMarginMultiplier);
  }

  public void setSafeMargin(Vector3 halfExtents) {
    gdxBulletJNI.btConvexInternalShape_setSafeMargin__SWIG_3(swigCPtr, this, halfExtents);
  }

  public Vector3 getLocalScalingNV() {
	return gdxBulletJNI.btConvexInternalShape_getLocalScalingNV(swigCPtr, this);
}

  public float getMarginNV() {
    return gdxBulletJNI.btConvexInternalShape_getMarginNV(swigCPtr, this);
  }

}
