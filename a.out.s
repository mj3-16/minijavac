	.text
# -- Begin  mjMain
	.p2align 4,,15
	.globl mjMain
	.type	mjMain, @function
mjMain:
.L0:                              /* Block BB[56:2] preds: none, freq: 1,000 */
	pushq %rbp                       /* amd64_push_reg T[232:96] */
	mov %rsp, %rbp                   /* be_Copy Lu[235:99] */
	subq $16, %rsp                   /* be_IncSP Lu[236:100] */
	movq %r14, -8(%rbp)              /* amd64_mov_store M[204:72] */
	movq %r15, -16(%rbp)             /* amd64_mov_store M[201:69] */
	movl $0x1, %r15d                 /* amd64_mov_imm Lu[155:27] */
	xorl %r14d, %r14d                /* amd64_xor_0 T[240:104] */
	jmp .L1                          /* amd64_jmp X[152:24] */
.L2:                              /* Block BB[70:25] preds: .L1, freq: 10,000 */
	mov %r15, %rdi                   /* be_Copy Lu[198:68] */
	call print_int                   /* amd64_call T[156:28] */
	addl $1, %r14d                   /* amd64_add T[238:102] */
	/* fallthrough to .L1 */         /* amd64_jmp X[154:26] */
.L1:                              /* Block BB[65:10] preds: .L0 .L2, freq: 11,000 */
	cmpl $0, %r14d                   /* amd64_cmp T[148:20] */
	je .L2                           /* amd64_jcc T[150:22] */
	/* fallthrough to .L3 */         /* amd64_jcc T[150:22] */
.L3:                              /* Block BB[114:9] preds: .L1, freq: 1,000 */
	movq -16(%rbp), %r15             /* amd64_mov_gp T[202:70] */
	movq -8(%rbp), %r14              /* amd64_mov_gp T[205:73] */
	leave                            /* amd64_leave T[228:92] */
	ret                              /* amd64_ret X[146:18] */
	.size	mjMain, .-mjMain
# -- End  mjMain

