package pl.matixhaker.antyhapek;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public class DeobfuscationTransformer implements Transformer {
  private final List<MethodNode> clearMethodNodes = new ArrayList<>();
  private final Set<FieldNode> clearFieldNodes = new HashSet<>();

  private static String encrypt(String str, int y) {
    char[] x = str.toCharArray();
    for (int i = 0; i < x.length; i++) x[i] = (char) (x[i] ^ y);

    return new String(x);
  }

  private static boolean isInteger(AbstractInsnNode abstractInsnNode) {
    return abstractInsnNode != null
            && abstractInsnNode.getOpcode() >= Opcodes.ICONST_M1
            && abstractInsnNode.getOpcode() <= Opcodes.ICONST_5
        || abstractInsnNode instanceof IntInsnNode
        || abstractInsnNode instanceof LdcInsnNode
            && ((LdcInsnNode) abstractInsnNode).cst instanceof Integer;
  }

  private static int getInteger(AbstractInsnNode node) {
    if (node.getOpcode() >= Opcodes.ICONST_M1 && node.getOpcode() <= Opcodes.ICONST_5)
      return node.getOpcode() - 3;
    if (node instanceof IntInsnNode) return ((IntInsnNode) node).operand;
    if (node instanceof LdcInsnNode) {
      LdcInsnNode ldc = (LdcInsnNode) node;
      if (ldc.cst instanceof Integer) return (int) ldc.cst;
    }
    return 0;
  }

  private static AbstractInsnNode createInteger(int number) {
    if (number >= -1 && number <= 5) return new InsnNode(number + Opcodes.ICONST_0);
    else if (number >= -128 && number <= 127) return new IntInsnNode(Opcodes.BIPUSH, number);
    else if (number >= -32768 && number <= 32767) return new IntInsnNode(Opcodes.SIPUSH, number);
    else return new LdcInsnNode(number);
  }

  private static boolean oneOf(int x, int... y) {
    for (int z : y) if (x == z) return true;
    return false;
  }

  public void transform(ClassNode node) {
    Optional<MethodNode> clinit =
        node.methods.stream().filter(m -> m.name.equals("<clinit>")).findFirst();
    if (!clinit.isPresent()) return;

    boolean znalazlo;

    do {
      znalazlo = false;

      for (AbstractInsnNode insnNode : clinit.get().instructions) {

        if (insnNode instanceof LdcInsnNode
            && ((LdcInsnNode) insnNode).cst instanceof Long
            && insnNode.getNext() != null
            && insnNode.getNext() instanceof LdcInsnNode
            && ((LdcInsnNode) insnNode.getNext()).cst instanceof Long
            && insnNode.getNext().getNext() != null
            && oneOf(
                insnNode.getNext().getNext().getOpcode(),
                Opcodes.LXOR,
                Opcodes.LOR,
                Opcodes.LAND)) {
          Long val1 = (Long) ((LdcInsnNode) insnNode).cst;
          Long val2 = (Long) ((LdcInsnNode) insnNode).cst;
          long result;

          int calculationOpcode = insnNode.getNext().getNext().getOpcode();
          switch (calculationOpcode) {
            case Opcodes.LXOR:
              result = val1 ^ val2;
              break;
            case Opcodes.LOR:
              result = val1 | val2;
              break;
            case Opcodes.LAND:
              result = val1 & val2;
              break;
            default:
              continue;
          }

          clinit.get().instructions.remove(insnNode.getNext().getNext());
          clinit.get().instructions.remove(insnNode.getNext());
          clinit.get().instructions.set(insnNode, new LdcInsnNode(result));
          znalazlo = true;
        }
      }
    } while (znalazlo);

    do {
      znalazlo = false;

      for (AbstractInsnNode insnNode : clinit.get().instructions) {

        if (isInteger(insnNode)
            && isInteger(insnNode.getNext())
            && oneOf(
                insnNode.getNext().getNext().getOpcode(),
                Opcodes.IXOR,
                Opcodes.IOR,
                Opcodes.IAND)) {
          int val1 = getInteger(insnNode);
          int val2 = getInteger(insnNode.getNext());

          int result;

          int calculationOpcode = insnNode.getNext().getNext().getOpcode();
          switch (calculationOpcode) {
            case Opcodes.IXOR:
              result = val1 ^ val2;
              break;
            case Opcodes.IOR:
              result = val1 | val2;
              break;
            case Opcodes.IAND:
              result = val1 & val2;
              break;
            default:
              continue;
          }

          clinit.get().instructions.remove(insnNode.getNext().getNext());
          clinit.get().instructions.remove(insnNode.getNext());
          clinit.get().instructions.set(insnNode, createInteger(result));
          znalazlo = true;
        }
      }
    } while (znalazlo);

    do {
      znalazlo = false;
      for (MethodNode node1 : node.methods)
        for (AbstractInsnNode node2 : node1.instructions) {
          if (node2.getOpcode() == Opcodes.GETSTATIC
              && ((FieldInsnNode) node2).owner.equals(node.name)
              && isInteger(node2.getNext())
              && node2.getNext().getNext() != null
              && node2.getNext().getNext().getOpcode() == Opcodes.IALOAD) {
            int position = getInteger(node2.getNext());

            node1.instructions.remove(node2.getNext().getNext());
            node1.instructions.remove(node2.getNext());

            int integerArrayValue = 0;
            for (AbstractInsnNode clinitInstruction : clinit.get().instructions)
              if (clinitInstruction.getOpcode() == Opcodes.GETSTATIC
                  && isInteger(clinitInstruction.getNext())
                  && isInteger(clinitInstruction.getNext().getNext())
                  && clinitInstruction.getNext().getNext().getNext().getOpcode() == Opcodes.IASTORE)
                if (getInteger(clinitInstruction.getNext()) == position) {
                  FieldInsnNode fieldInsnNode = ((FieldInsnNode) clinitInstruction);
                  clearFieldNodes.add(
                      node.fields.stream()
                          .filter(
                              m ->
                                  m.name.equals(fieldInsnNode.name)
                                      && m.desc.equals(fieldInsnNode.desc))
                          .findFirst()
                          .get());

                  int i = getInteger(clinitInstruction.getNext().getNext());
                  clinit.get().instructions.remove(clinitInstruction.getNext().getNext().getNext());
                  clinit.get().instructions.remove(clinitInstruction.getNext().getNext());
                  clinit.get().instructions.remove(clinitInstruction.getNext());
                  clinit.get().instructions.remove(clinitInstruction);
                  integerArrayValue = i;
                }
            node1.instructions.set(node2, createInteger(integerArrayValue));
            znalazlo = true;
          }
        }
    } while (znalazlo);

    do {
      znalazlo = false;
      for (MethodNode node1 : node.methods)
        for (AbstractInsnNode node2 : node1.instructions) {
          if (node2.getOpcode() == Opcodes.GETSTATIC
              && ((FieldInsnNode) node2).owner.equals(node.name)
              && isInteger(node2.getNext())
              && node2.getNext().getNext() != null
              && node2.getNext().getNext().getOpcode() == Opcodes.LALOAD) {
            int position = getInteger(node2.getNext());

            long arrayValue = 0;

            node1.instructions.remove(node2.getNext().getNext());
            node1.instructions.remove(node2.getNext());

            for (AbstractInsnNode clinitInstruction : clinit.get().instructions)
              if (clinitInstruction.getOpcode() == Opcodes.GETSTATIC
                  && isInteger(clinitInstruction.getNext())
                  && clinitInstruction.getNext().getNext() instanceof LdcInsnNode
                  && clinitInstruction.getNext().getNext().getNext().getOpcode() == Opcodes.LASTORE)
                if (getInteger(clinitInstruction.getNext()) == position) {
                  long l = (long) ((LdcInsnNode) clinitInstruction.getNext().getNext()).cst;

                  FieldInsnNode fieldInsnNode = ((FieldInsnNode) clinitInstruction);
                  clearFieldNodes.add(
                      node.fields.stream()
                          .filter(
                              m ->
                                  m.name.equals(fieldInsnNode.name)
                                      && m.desc.equals(fieldInsnNode.desc))
                          .findFirst()
                          .get());

                  clinit.get().instructions.remove(clinitInstruction.getNext().getNext().getNext());
                  clinit.get().instructions.remove(clinitInstruction.getNext().getNext());
                  clinit.get().instructions.remove(clinitInstruction.getNext());
                  clinit.get().instructions.remove(clinitInstruction);
                  arrayValue = l;
                }

            node1.instructions.set(node2, new LdcInsnNode(arrayValue));
            znalazlo = true;
          }
        }
    } while (znalazlo);

    do {
      znalazlo = false;
      for (MethodNode node1 : node.methods)
        for (AbstractInsnNode node2 : node1.instructions) {
          if (node2.getOpcode() == Opcodes.GETSTATIC
              && ((FieldInsnNode) node2).owner.equals(node.name)
              && isInteger(node2.getNext())
              && node2.getNext().getNext() != null
              && node2.getNext().getNext().getOpcode() == Opcodes.AALOAD) {
            int position = getInteger(node2.getNext());

            node1.instructions.remove(node2.getNext().getNext());
            node1.instructions.remove(node2.getNext());

            String stringArrayValue = null;
            for (AbstractInsnNode clinitNode : clinit.get().instructions)
              if (clinitNode.getOpcode() == Opcodes.GETSTATIC
                  && isInteger(clinitNode.getNext())
                  && clinitNode.getNext().getNext() instanceof LdcInsnNode
                  && clinitNode.getNext().getNext().getNext() != null
                  && clinitNode.getNext().getNext().getNext().getOpcode() == Opcodes.INVOKESTATIC
                  && clinitNode.getNext().getNext().getNext().getNext().getOpcode()
                      == Opcodes.AASTORE)
                if (getInteger(clinitNode.getNext()) == position) {
                  String value = (String) ((LdcInsnNode) clinitNode.getNext().getNext()).cst;

                  MethodInsnNode methodInsnNode =
                      ((MethodInsnNode) clinitNode.getNext().getNext().getNext());

                  MethodNode encryptionMethod =
                      node.methods.stream()
                          .filter(
                              m ->
                                  m.name.equals(methodInsnNode.name)
                                      && m.desc.equals(methodInsnNode.desc))
                          .findFirst()
                          .get();

                  int encryptionKey = 0;
                  for (AbstractInsnNode nodeXd : encryptionMethod.instructions) {
                    if (nodeXd.getOpcode() == Opcodes.IXOR
                        && isInteger(nodeXd.getNext())
                        && nodeXd.getNext().getNext() != null
                        && nodeXd.getNext().getNext().getOpcode() == Opcodes.IXOR) {
                      encryptionKey = getInteger(nodeXd.getNext());
                      break;
                    }
                  }

                  FieldInsnNode fieldInsnNode = ((FieldInsnNode) clinitNode);
                  clearFieldNodes.add(
                      node.fields.stream()
                          .filter(
                              m ->
                                  m.name.equals(fieldInsnNode.name)
                                      && m.desc.equals(fieldInsnNode.desc))
                          .findFirst()
                          .get());

                  clinit
                      .get()
                      .instructions
                      .remove(clinitNode.getNext().getNext().getNext().getNext());
                  clinit.get().instructions.remove(clinitNode.getNext().getNext().getNext());
                  clinit.get().instructions.remove(clinitNode.getNext().getNext());
                  clinit.get().instructions.remove(clinitNode.getNext());
                  clinit.get().instructions.remove(clinitNode);

                  clearMethodNodes.add(encryptionMethod);
                  stringArrayValue =
                      encrypt(
                          value,
                          node.name.replace('/', '.').hashCode()
                              ^ "<clinit>".hashCode()
                              ^ encryptionMethod.name.hashCode()
                              ^ encryptionKey);
                }

            node1.instructions.set(node2, new LdcInsnNode(stringArrayValue));
            znalazlo = true;
          }
        }
    } while (znalazlo);

    node.methods.removeIf(clearMethodNodes::contains);
    node.fields.removeIf(clearFieldNodes::contains);

    do {
      znalazlo = false;
      for (AbstractInsnNode abstractInsnNode : clinit.get().instructions) {
        if (isInteger(abstractInsnNode) &&
            abstractInsnNode.getNext() != null
            && oneOf(abstractInsnNode.getNext().getOpcode(), Opcodes.ANEWARRAY, Opcodes.NEWARRAY)
            && abstractInsnNode.getNext().getNext().getOpcode() == Opcodes.PUTSTATIC) {
          FieldInsnNode insnNode = (FieldInsnNode) abstractInsnNode.getNext().getNext();

          if (clearFieldNodes.stream()
              .anyMatch(f -> f.name.equals(insnNode.name) && f.desc.equals(insnNode.desc))) {
            clinit.get().instructions.remove(abstractInsnNode.getNext().getNext());
            clinit.get().instructions.remove(abstractInsnNode.getNext());
            clinit.get().instructions.remove(abstractInsnNode);
            znalazlo = true;
          }
        }
      }

    } while (znalazlo);

    for(MethodNode node1: node.methods)
      node1.localVariables = null;
    /*
    array reference (getstatic)
    integer
    value
    store
     */
  }

  /*
          bipush 49
            newarray 10
            putstatic  IlllIllllllllIIlIlllllIllIllIIlIlIIlIIllIlllIIIlII.lIIIIIIIIllIIllllIlIlIlIl:int[]
            bipush 6
            anewarray java/lang/String
            putstatic  IlllIllllllllIIlIlllllIllIllIIlIlIIlIIllIlllIIIlII.IlIllIlllIIIlllIIIIIIlIIl:java.lang.String[]

  */
}
