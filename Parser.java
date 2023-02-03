import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static java.lang.Integer.parseUnsignedInt;

public class Parser {
    private final int[] bytes;
    private int tagCount;
    private int textOffset;
    private int textSize;
    private int textAddr;
    private int symtabOffset;
    private int symtabSize;
    private int strtabOffset;
    private BufferedWriter out;
    private final Map<Integer, String> symTab = new HashMap<>();

    public Parser(String in, String out) {
        byte[] temp = new byte[0];
        try {
            temp = new FileInputStream(in).readAllBytes();
        } catch (FileNotFoundException e) {
            System.err.println("File not found: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Problems with file reading: " + e.getMessage());
        }
        bytes = new int[temp.length];
        for (int i = 0; i < temp.length; i++) {
            bytes[i] = (temp[i] >= 0) ? temp[i] : temp[i] + 256;
        }
        try {
            this.out = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(out), StandardCharsets.UTF_8));
            if (!(bytes[0] == 127 && bytes[1] == 69 && bytes[2] == 76 && bytes[3] == 70)) {
                throw new IOException("Not an ELF file");
            }
        } catch (IOException e) {
            System.err.println("Problems with file writing: " + e.getMessage());
        }
    }

    public void parse() {
        try {
            int cnt = 0;
            int e_shoff = get(32, 4);
            int e_shnum = get(48, 2);
            int e_shstrndx = get(50, 2);
            int sh_offset = get(e_shoff + 40 * e_shstrndx + 16, 4);
            for (int i = 0; i < e_shnum; i++) {
                int ind = e_shoff + 40 * i;
                String name = name(get(ind, 4) + sh_offset);
                switch (name) {
                    case ".text" -> {
                        textAddr = get(ind + 12, 4);
                        textOffset = get(ind + 16, 4);
                        textSize = get(ind + 20, 4);
                    }
                    case ".symtab" -> {
                        symtabOffset = get(ind + 16, 4);
                        symtabSize = get(ind + 20, 4);
                    }
                    case ".strtab" -> {
                        strtabOffset = get(ind + 16, 4);
                    }
                }
            }
            for (int i = symtabOffset; i < symtabOffset + symtabSize; i += 16) {
                if (get(i + 12, 1) % 16 == 2) {
                    int value = get(i + 4, 4);
                    symTab.put(value, name(strtabOffset + get(i, 4)));
                }
            }
            out.write(".text");
            out.newLine();
            for (int i = textOffset; i < textOffset + textSize; i += 4) {
                String command = toBin(bytes[i + 3]) + toBin(bytes[i + 2]) + toBin(bytes[i + 1]) + toBin(bytes[i]);
                parseCommand(command, i + (textAddr - textOffset));
                String sym = symTab.get(i + (textAddr - textOffset));
                if (sym != null) {
                    out.write(String.format("%08x   <%s>:", i + (textAddr - textOffset),
                            symTab.get(i + (textAddr - textOffset))));
                    out.newLine();
                }
                out.write(String.format("   %05x: \t%08x\t", i + (textAddr - textOffset),
                        parseUnsignedInt(command, 2)));
                out.write(parseCommand(command, (i + (textAddr - textOffset))));
                out.newLine();
            }
            out.newLine();
            out.write(".symtab");
            out.newLine();
            out.write(String.format("%s %-15s %7s %-8s %-8s %-8s %6s %s", "Symbol",
                    "Value", "Size", "Type", "Bind", "Vis", "Index", "Name"));
            out.newLine();
            for (int i = symtabOffset; i < symtabOffset + symtabSize; i += 16) {
                int value = get(i + 4, 4);
                int size = get(i + 8, 4);
                int info = get(i + 12, 1);
                out.write(String.format("[%4s] 0x%-15s %5s %-8s %-8s %-8s %6s %s", Integer.toHexString(cnt++),
                        Long.toHexString(value), size, type(info), bind(info),
                        visibility(get(i + 13, 1)), toIndex(get(i + 14, 2)),
                        name(strtabOffset + get(i, 4))));
                out.newLine();
            }
            try {
                out.close();
            } catch (IOException e) {
                System.out.println("Problems with closing the file: " + e.getMessage());
            }
        } catch (IOException e) {
            System.out.println("Problems with writing: " + e.getMessage());
        }
    }

    private String parseCommand(String s, int offset_byte) {
        String opcode = s.substring(25, 32);
        String rd = s.substring(20, 25);
        String rs1 = s.substring(12, 17);
        String rs2 = s.substring(7, 12);
        String funct7 = s.substring(0, 7);
        String funct3 = s.substring(17, 20);
        switch (opcode) {
            case "0110111" -> {
                int imm = twosComplement(s.substring(0, 20), 21);
                return String.format("%7s\t%s, %s", "lui", reg(rd), "0x" + Integer.toHexString(imm));
            }
            case "0010111" -> {
                int imm = twosComplement(s.substring(0, 20), 21);
                return String.format("%7s\t%s, %s", "auipc", reg(rd), imm);
            }
            case "1101111" -> {
                int imm = twosComplement(s.charAt(0) + s.substring(12, 20) +
                        s.charAt(12) + s.substring(1, 11) + "0", 21);
                if (!symTab.containsKey(offset_byte + imm)) {
                    symTab.put(offset_byte + imm, "L" + tagCount);
                    tagCount++;
                }
                return String.format("%7s\t%s, %s <%s>", "jal", reg(rd), imm, symTab.get(offset_byte + imm));
            }
            case "1100111" -> {
                int imm = twosComplement(s.substring(0, 12), 12);
                if (funct3.equals("000")) {
                    return String.format("%7s\t%s, %s(%s)", "jalr", reg(rd), imm, reg(rs1));
                }
                return "unknown_instruction";
            }
            case "1100011" -> {
                int imm = twosComplement(s.charAt(0) + s.substring(24, 25) +
                        s.substring(1, 7) + s.substring(20, 24) + "0", 13);
                if (!symTab.containsKey(offset_byte + imm)) {
                    symTab.put(offset_byte + imm, "L" + tagCount);
                    tagCount++;
                }
                return String.format("%7s\t%s, %s, %s <%s>",
                        branch(funct3), reg(rs1), reg(rs2),
                        ("0x" + Integer.toHexString(offset_byte + imm)),
                        symTab.get(offset_byte + imm));
            }
            case "0000011" -> {
                int imm = twosComplement(s.substring(0, 12), 12);
                return String.format("%7s\t%s, %s(%s)", load(funct3), reg(rd), imm, reg(rs1));
            }
            case "0100011" -> {
                int imm = twosComplement(s.substring(0, 7) + s.substring(20, 25), 12);
                return String.format("%7s\t%s, %s(%s)", store(funct3), reg(rs2), imm, reg(rs1));
            }
            case "0010011" -> {
                int imm = twosComplement(s.substring(0, 12), 12);
                switch (funct3) {
                    case "001" -> {
                        return String.format("%7s\t%s, %s, %s", irv(funct3), reg(rd), reg(rs1), imm >> 5);
                    }
                    case "101" -> {
                        switch (imm >>> 5) {
                            case 0 -> {
                                return String.format("%7s\t%s, %s, %s", "srli", reg(rd), reg(rs1), imm >> 5);
                            }
                            case 32 -> {
                                return String.format("%7s\t%s, %s, %s", "srai", reg(rd), reg(rs1), imm >> 5);
                            }
                            default -> {
                                return "unknown_instruction";
                            }
                        }
                    }
                    default -> {
                        return String.format("%7s\t%s, %s, %s", irv(funct3), reg(rd), reg(rs1), imm);
                    }
                }
            }
            case "0110011" -> {
                return String.format("%7s\t%s, %s, %s", rv(funct7, funct3), reg(rd), reg(rs1), reg(rs2));
            }
            case "0001111" -> {
                return String.format("%7s\t", "fence");
            }
            case "1110011" -> {
                String csr = s.substring(0, 12);
                if (funct3.equals("000") && rd.equals("00000")) {
                    return switch (csr) {
                        case "000000000000" -> String.format("%7s\t", "ecall");
                        case "000000000001" -> String.format("%7s\t", "ebreak");
                        default -> "unknown_instruction";
                    };
                }
                return "unknown_instruction";
            }
            default -> {
                return "unknown_instruction";
            }
        }
    }

    private int twosComplement(String x, int length) {
        int imm = parseUnsignedInt(x, 2);
        if (imm >= binPow(length - 1)) {
            imm -= binPow(length);
        }
        return imm;
    }

    private String name(int start) {
        StringBuilder sb = new StringBuilder();
        for (int j = start; j < bytes.length; j++) {
            if (bytes[j] == 0) {
                break;
            }
            sb.append((char) bytes[j]);
        }
        return sb.toString();
    }

    private String toBin(int b) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            sb.insert(0, (b % 2));
            b /= 2;
        }
        return sb.toString();
    }
    private int binPow(int b) {
        int a = 2;
        int ans = 1;
        while (b > 0) {
            if (b % 2 == 1) {
                ans = ans * a;
            }
            a = a * a;
            b = b / 2;
        }
        return ans;
    }

    private int get(int start, int cnt) {
        int result = 0;
        for (int i = cnt - 1; i >= 0; i--) {
            result = (result << 8) + bytes[start + i];
        }
        return result;
    }

    private String reg(String x) {
        return reg[parseUnsignedInt(x, 2)];
    }

    private static final String[] reg = {"zero", "ra", "sp", "gp", "tp", "t0", "t1", "t2",
            "s0", "s1", "a0", "a1", "a2", "a3", "a4", "a5", "a6", "a7", "s2", "s3", "s4", "s5",
            "s6", "s7", "s8", "s9", "s10", "s11", "t3", "t4", "t5", "t6"};

    private String toIndex(int st_shndx) {
        return switch (st_shndx) {
            case 0 -> "UNDEF";
            case 0xff00 -> "LOPROC";
            case 0xff01 -> "AFTER";
            case 0xff02 -> "AMD64_LCOMMON";
            case 0xff1f -> "HIPROC";
            case 0xff20 -> "LOOS";
            case 0xff3f -> "HIOS";
            case 0xfff1 -> "ABS";
            case 0xfff2 -> "COMMON";
            case 0xffff -> "XINDEX";
            default -> st_shndx + "";
        };
    }

    private String type(int info) {
        return switch ((info & 0xf)) {
            case 0 -> "NOTYPE";
            case 1 -> "OBJECT";
            case 2 -> "FUNC";
            case 3 -> "SECTION";
            case 4 -> "FILE";
            case 5 -> "COMMON";
            case 6 -> "TLS";
            case 10 -> "LOOS";
            case 12 -> "HIOS";
            case 13 -> "LOPROC";
            case 15 -> "HIPROC";
            default -> "UNKNOWN";
        };
    }

    private String visibility(int info) {
        return switch (info) {
            case 0 -> "DEFAULT";
            case 1 -> "INTERNAL";
            case 2 -> "HIDDEN";
            case 3 -> "PROTECTED";
            default -> "UNKNOWN";
        };
    }

    private String bind(int info) {
        return switch (info >> 4) {
            case 0 -> "LOCAL";
            case 1 -> "GLOBAL";
            case 2 -> "WEAK";
            case 10 -> "LOOS";
            case 12 -> "HIOS";
            case 13 -> "LOPROC";
            case 15 -> "HIPROC";
            default -> "UNKNOWN";
        };
    }

    private String rv(String funct7, String funct3) {
        return switch (funct7) {
            case "0000000" -> switch (funct3) {
                case "000" -> "add";
                case "001" -> "sll";
                case "010" -> "slt";
                case "011" -> "sltu";
                case "100" -> "xor";
                case "101" -> "srl";
                case "110" -> "or";
                case "111" -> "and";
                default -> "unknown_instruction";
            };
            case "0100000" -> switch (funct3) {
                case "000" -> "sub";
                case "101" -> "sra";
                default -> "unknown_instruction";
            };
            case "0000001" -> switch (funct3) {
                case "000" -> "mul";
                case "001" -> "mulh";
                case "010" -> "mulhsu";
                case "011" -> "mulhu";
                case "100" -> "div";
                case "101" -> "divu";
                case "110" -> "rem";
                case "111" -> "remu";
                default -> "unknown_instruction";
            };
            default -> "unknown_instruction";
        };
    }

    private String irv(String funct3) {
        return switch (funct3) {
            case "000" -> "addi";
            case "001" -> "slli";
            case "010" -> "slti";
            case "011" -> "sltiu";
            case "100" -> "xori";
            case "110" -> "ori";
            case "111" -> "andi";
            default -> "unknown_instruction";
        };
    }

    private String load(String funct3) {
        return switch (funct3) {
            case "000" -> "lb";
            case "001" -> "lh";
            case "010" -> "lw";
            case "100" -> "lbu";
            case "101" -> "lhu";
            default -> "unknown_instruction";
        };
    }


    private String store(String funct3) {
        return switch (funct3) {
            case "000" -> "sb";
            case "001" -> "sh";
            case "010" -> "sw";
            default -> "unknown_instruction";
        };
    }

    private String branch(String funct3) {
        return switch (funct3) {
            case "000" -> "beq";
            case "001" -> "bne";
            case "100" -> "blt";
            case "101" -> "bge";
            case "110" -> "bltu";
            case "111" -> "bgeu";
            default -> "unknown_instruction";
        };
    }
}