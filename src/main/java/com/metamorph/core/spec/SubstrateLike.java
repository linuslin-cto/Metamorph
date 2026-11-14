package com.metamorph.core.spec;

import com.metamorph.core.tree.SurfaceSpec;

/** What core needs of a substrate. */
public interface SubstrateLike {
  String name();

  SurfaceSpec surface();
}
