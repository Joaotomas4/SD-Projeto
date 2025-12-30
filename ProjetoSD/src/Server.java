import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;

public class Server {
    public static void main(String[] args) {
        // 1. Configurações iniciais
        int D = 10; // Retenção total de dias (exigência do guião)
        int S = 5;  // Limite de séries temporais em memória RAM
        int porto = 12345;

        try {
            // 2. Inicialização do motor lógico (TSDB)
            TSDB tsdb = new TSDB(D, S);
            ServerSocket serverSocket = new ServerSocket(porto);

            System.out.println("#########################################");
            System.out.println("###   SERVIDOR TSDB STORE - ATIVO     ###");
            System.out.println("###   Porto: " + porto + "                    ###");
            System.out.println("###   Config: D=" + D + ", S=" + S + "               ###");
            System.out.println("#########################################");
            System.out.println("Dica: Prime ENTER para mudar para o próximo dia.");

            // 3. Thread de Gestão de Administração (Mudar o Dia)
            // Permite testar a persistência e limpeza de RAM sem desligar o servidor
            new Thread(() -> {
                Scanner sc = new Scanner(System.in);
                while (true) {
                    sc.nextLine(); // Espera por um Enter na consola do servidor
                    System.out.println("[ADMIN] A fechar o dia e persistir dados...");
                    tsdb.proximoDia();
                    System.out.println("[ADMIN] Dia mudado com sucesso. Novo dia iniciado.");
                }
            }).start();

            // 4. Ciclo Principal de Aceitação de Clientes
            while (true) {
                // Fica bloqueado aqui até que um cliente faça "new Socket()"
                Socket clientSocket = serverSocket.accept();
                System.out.println("[REDE] Novo cliente conectado: " + clientSocket.getInetAddress());

                // 5. Criação e lançamento do Worker
                // Cada cliente recebe a sua própria thread para não bloquear os outros
                ServerWorker worker = new ServerWorker(tsdb, clientSocket);
                Thread workerThread = new Thread(worker);
                workerThread.start();
            }

        } catch (Exception e) {
            System.err.println("[ERRO CRÍTICO] Falha ao iniciar o servidor: " + e.getMessage());
            e.printStackTrace();
        }
    }
}