# Elf Parser

## Elf-file parser that extracts `.text` and `.symtab` from the elf file.

### Supports only RISC-V RV32I, RV32M 
### If Elf file contains a command which is not listed here, parser will output `unknown_command`.


#### Sample of `.text` output
```
.text
00010074   <main>:
   10074: 	ff010113	   addi	sp, sp, -16
   10078: 	00112623	     sw	ra, 12(sp)
   1007c: 	030000ef	    jal	ra, 48 <mmul>
   10080: 	00c12083	     lw	ra, 12(sp)
   10084: 	00000513	   addi	a0, zero, 0
   10088: 	01010113	   addi	sp, sp, 16
   1008c: 	00008067	   jalr	zero, 0(ra)
   10090: 	00000013	   addi	zero, zero, 0
   10094: 	00100137	    lui	sp, 0x100
   10098: 	fddff0ef	    jal	ra, -36 <main>
   1009c: 	00050593	   addi	a1, a0, 0
   100a0: 	00a00893	   addi	a7, zero, 10
   100a4: 	0ff0000f	  fence	
   100a8: 	00000073	  ecall	
...
```

#### Sample of `.symtab` output
```
.symtab
Symbol Value              Size Type     Bind     Vis       Index Name
[   0] 0x0                   0 NOTYPE   LOCAL    DEFAULT   UNDEF 
[   1] 0x10074               0 SECTION  LOCAL    DEFAULT       1 
[   2] 0x11124               0 SECTION  LOCAL    DEFAULT       2 
[   3] 0x0                   0 SECTION  LOCAL    DEFAULT       3 
[   4] 0x0                   0 SECTION  LOCAL    DEFAULT       4 
[   5] 0x0                   0 FILE     LOCAL    DEFAULT     ABS test.c
[   6] 0x11924               0 NOTYPE   GLOBAL   DEFAULT     ABS __global_pointer$
[   7] 0x118f4             800 OBJECT   GLOBAL   DEFAULT       2 b
[   8] 0x11124               0 NOTYPE   GLOBAL   DEFAULT       1 __SDATA_BEGIN__
[   9] 0x100ac             120 FUNC     GLOBAL   DEFAULT       1 mmul
[   a] 0x0                   0 NOTYPE   GLOBAL   DEFAULT   UNDEF _start
[   b] 0x11124            1600 OBJECT   GLOBAL   DEFAULT       2 c
[   c] 0x11c14               0 NOTYPE   GLOBAL   DEFAULT       2 __BSS_END__
[   d] 0x11124               0 NOTYPE   GLOBAL   DEFAULT       2 __bss_start
[   e] 0x10074              28 FUNC     GLOBAL   DEFAULT       1 main
[   f] 0x11124               0 NOTYPE   GLOBAL   DEFAULT       1 __DATA_BEGIN__
[  10] 0x11124               0 NOTYPE   GLOBAL   DEFAULT       1 _edata
[  11] 0x11c14               0 NOTYPE   GLOBAL   DEFAULT       2 _end
[  12] 0x11764             400 OBJECT   GLOBAL   DEFAULT       2 a
