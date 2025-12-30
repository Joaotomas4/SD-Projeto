import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Demultiplexer implements AutoCloseable {
    private final TaggedConnection conn;
    private final Lock lock = new ReentrantLock();
    private int lastTag = 0; // Contador simples para gerar IDs
    private final Map<Integer, Entry> waiters = new HashMap<>();
    private IOException exception = null; // Para propagar erros de rede

    private class Entry {
        Frame frame = null; // Onde a resposta será depositada
        Condition cond;     // A campainha específica para esta Tag
        int waiters = 0;    // Para controlo opcional de limpeza

        Entry(Condition cond) {
            this.cond = cond;
        }
    }

    private int getNextTag() {
        // Não precisa de lock próprio se for chamado dentro de um bloco
        // que já tem o lock.lock() do Demultiplexer.
        return ++lastTag;
    }

    public Demultiplexer(TaggedConnection conn) {
        this.conn = conn;
        // Lançar a thread de leitura (Receiver)
        new Thread(() -> {
            try {
                while (true) {
                    Frame f = conn.receive(); // Fora do lock para não bloquear o socket
                    lock.lock();
                    try {
                        Entry e = waiters.get(f.tag);
                        if (e == null) {
                            // Caso estranho: resposta para algo que não pedimos
                            // (ou o cliente desistiu por timeout)
                        } else {
                            e.frame = f;
                            e.cond.signal(); // Acorda a thread específica
                        }
                    } finally {
                        lock.unlock();
                    }
                }
            } catch (IOException e) {
                lock.lock();
                try {
                    this.exception = e;
                    // Acordar toda a gente se a rede morrer
                    for (Entry entry : waiters.values()) entry.cond.signalAll();
                } finally {
                    lock.unlock();
                }
            }
        }).start();
    }

    public byte[] send(int opCode, byte[] data) throws IOException, InterruptedException {
        int tag;
        Entry e;

        lock.lock();
        try {
            // 1. Gerar Tag única (pode ser um contador global)
            tag = getNextTag();

            // 2. Criar entrada no mapa
            e = new Entry(lock.newCondition());
            waiters.put(tag, e);
        } finally {
            lock.unlock();
        }

        // 3. Enviar o frame (usando o lock de escrita da TaggedConnection)
        conn.send(tag, opCode, data);

        lock.lock();
        try {
            // 4. Esperar pela resposta (Ciclo contra Spurious Wakeups)
            while (e.frame == null && exception == null) {
                e.cond.await();
            }

            if (exception != null) throw exception;

            // 5. Limpar e retornar
            waiters.remove(tag);
            return e.frame.payload;
        } finally {
            lock.unlock();
        }
    }



    @Override
    public void close() throws IOException {
        // Ao fechar a ligação, a thread de leitura irá receber uma IOException
        // e o nosso mecanismo de erro (que já escreveste) irá acordar todas as threads.
        conn.close();
    }

}