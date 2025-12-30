import java.util.Scanner;

public class ClientMenu {
    private static ClientAPI api;
    private static Scanner sc = new Scanner(System.in);
    private static boolean autenticado = false;

    public static void main(String[] args) {
        try {
            // Liga ao servidor (ajusta o host/porto se necessário)
            api = new ClientAPI("localhost", 12345);
            System.out.println("### Bem-vindo à TSDB Store ###");

            while (true) {
                if (!autenticado) {
                    menuAutenticacao();
                } else {
                    menuPrincipal();
                }
            }
        } catch (Exception e) {
            System.err.println("Erro na ligação ao servidor: " + e.getMessage());
        }
    }

    // --- MENU INICIAL (LOGIN/REGISTO) ---

    private static void menuAutenticacao() {
        System.out.println("\n1: Registar");
        System.out.println("2: Login");
        System.out.print("> ");

        String opcao = sc.nextLine();
        try {
            if (opcao.equals("1")) {
                System.out.print("Username: "); String u = sc.nextLine();
                System.out.print("Password: "); String p = sc.nextLine();
                String res = api.register(u, p);
                System.out.println(res);
            }
            else if (opcao.equals("2")) {
                System.out.print("Username: "); String u = sc.nextLine();
                System.out.print("Password: "); String p = sc.nextLine();

                String res = api.login(u, p);
                System.out.println(res); // Imprime "ERRO: Credenciais inválidas." ou "Login efetuado..."

                // VERIFICAÇÃO: Só passamos para o menu principal se NÃO houver "ERRO" na resposta
                if (!res.contains("ERRO")) {
                    autenticado = true;
                } else {
                    System.out.println("Tente novamente.");
                }
            }
        } catch (Exception e) {
            System.out.println("Erro de conexão: " + e.getMessage());
        }
    }

    // --- MENU DE OPERAÇÕES (APÓS LOGIN) ---

    private static void menuPrincipal() {
        System.out.println("\n--- MENU PRINCIPAL ---");
        System.out.println("1: registar evento");
        System.out.println("2: get quantidade");
        System.out.println("3: get volume");
        System.out.println("4: get avg price");
        System.out.println("5: get max price");
        System.out.println("6: wait simultaneous sales");
        System.out.println("7: wait consecutive sales");
        System.out.println("0: Sair");
        System.out.print("> ");

        String opcao = sc.nextLine();
        try {
            switch (opcao) {
                case "1":
                    System.out.print("Produto: "); String prod = sc.nextLine();
                    System.out.print("Quantidade: "); int qtd = Integer.parseInt(sc.nextLine());
                    System.out.print("Preço: "); double preco = Double.parseDouble(sc.nextLine());
                    System.out.println(api.addEvent(prod, qtd, preco)); //
                    break;

                case "2":
                    System.out.print("Produto: "); String p2 = sc.nextLine();
                    System.out.print("Dias atrás (1-D): "); int d2 = Integer.parseInt(sc.nextLine());
                    System.out.println("Quantidade Total: " + api.getQuantity(p2, d2));
                    break;

                case "3":
                    System.out.print("Produto: "); String p3 = sc.nextLine();
                    System.out.print("Dias atrás (1-D): "); int d3 = Integer.parseInt(sc.nextLine());
                    System.out.println("Volume Total: " + api.getVolume(p3, d3));
                    break;

                case "4":
                    System.out.print("Produto: "); String p4 = sc.nextLine();
                    System.out.print("Dias atrás (1-D): "); int d4 = Integer.parseInt(sc.nextLine());
                    System.out.println("Preço Médio: " + api.getAvgPrice(p4, d4));
                    break;

                case "5":
                    System.out.print("Produto: "); String p5 = sc.nextLine();
                    System.out.print("Dias atrás (1-D): "); int d5 = Integer.parseInt(sc.nextLine());
                    System.out.println("Preço Máximo: " + api.getMaxPrice(p5, d5));
                    break;

                case "6":
                    System.out.print("Produto 1: "); String s1 = sc.nextLine();
                    System.out.print("Produto 2: "); String s2 = sc.nextLine();
                    System.out.println("Aguardando vendas simultâneas...");
                    System.out.println(api.waitSimultaneous(s1, s2)); // Bloqueia
                    break;

                case "7":
                    System.out.print("Produto: "); String cp = sc.nextLine();
                    System.out.print("Número de vendas (N): "); int cn = Integer.parseInt(sc.nextLine());
                    System.out.println("Aguardando vendas consecutivas...");
                    System.out.println(api.waitConsecutive(cp, cn)); // Bloqueia
                    break;

                case "0":
                    System.exit(0);
                    break;

                default:
                    System.out.println("Opção inválida.");
            }
        } catch (Exception e) {
            System.out.println("Erro na operação: " + e.getMessage());
        }
    }
}