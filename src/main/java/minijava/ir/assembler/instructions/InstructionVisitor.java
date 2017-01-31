package minijava.ir.assembler.instructions;

public interface InstructionVisitor<T> {

  T visit(MethodPrologue prologue);

  T visit(Add add);

  T visit(AllocStack allocStack);

  T visit(And and);

  T visit(Call call);

  T visit(CLTD cltd);

  T visit(Cmp cmp);

  T visit(ConditionalJmp jmp);

  T visit(DeallocStack deallocStack);

  T visit(Div div);

  T visit(Evict evict);

  T visit(Jmp jmp);

  T visit(MetaCall metaCall);

  T visit(Mov mov);

  T visit(MovFromSmallerToGreater mov);

  T visit(Mul mul);

  T visit(Neg neg);

  T visit(Pop pop);

  T visit(Push push);

  T visit(Ret ret);

  T visit(Set set);

  T visit(Sub sub);

  T visit(DisableRegisterUsage disableRegisterUsage);

  T visit(EnableRegisterUsage enableRegisterUsage);
}
