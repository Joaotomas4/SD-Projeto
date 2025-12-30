import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class Frame {
    public final int tag;
    public final int opCode;
    public final byte[] payload;

    public Frame(int tag, int opCode, byte[] payload) {
        this.tag = tag;
        this.opCode = opCode;
        this.payload = payload;
    }

    // Método para escrever o frame no socket
    public void serialize(DataOutputStream out) throws IOException {
        int totalSize = 4 + 4 + payload.length; // Tag(4) + OpCode(4) + Payload
        out.writeInt(totalSize);
        out.writeInt(tag);
        out.writeInt(opCode);
        out.write(payload);
        out.flush();
    }

    // Método para ler um frame do socket
    public static Frame deserialize(DataInputStream in) throws IOException {
        int totalSize = in.readInt();
        int tag = in.readInt();
        int opCode = in.readInt();
        byte[] payload = new byte[totalSize - 8]; // Removemos os 8 bytes da tag e opcode
        in.readFully(payload);
        return new Frame(tag, opCode, payload);
    }
}