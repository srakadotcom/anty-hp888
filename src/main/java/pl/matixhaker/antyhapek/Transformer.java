package pl.matixhaker.antyhapek;

import org.objectweb.asm.tree.ClassNode;

public interface Transformer {
  void transform(ClassNode node);
}
