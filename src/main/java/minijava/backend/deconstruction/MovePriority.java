package minijava.backend.deconstruction;

enum MovePriority {
  IMMEDIATE_SRC, // Could and should be handled last as it just makes the scheduling situation worse.
  MEM_MEM_UNSAFE, // Needs an exchange through Push Pop
  REG_MEM_UNSAFE, // Needs a Reg/Mem XCHG or a Push Pop (Dunno whats more expensive)
  REG_REG_UNSAFE, // Needs a Reg/Reg XCHG, which is cheap
  MEM_REG_SAFE, // This might block a scratch register
  MEM_MEM_SAFE, // Somewhat expensive because of Push Pop, but at least no exchange necessary
  REG_REG_SAFE, // Cheap as pie, but no real further advantage
  REG_MEM_SAFE // We maybe free a scratch register
}
