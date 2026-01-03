import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class ClientMenu {
    private static ClientAPI api;
    private static Scanner sc = new Scanner(System.in);
    private static boolean autenticado = false;

    public static void main(String[] args) {
        try {
            // Ajusta o host/porto se necessário
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

    // --- MÉTODO AUXILIAR PARA PAUSAR ---
    private static void primaEnter() {
        System.out.println("\nPrima [ENTER] para voltar ao menu...");
        sc.nextLine();
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
                primaEnter();
            }
            else if (opcao.equals("2")) {
                System.out.print("Username: "); String u = sc.nextLine();
                System.out.print("Password: "); String p = sc.nextLine();

                String res = api.login(u, p);
                System.out.println(res);

                if (!res.contains("ERRO")) {
                    autenticado = true;
                } else {
                    System.out.println("Tente novamente.");
                    primaEnter();
                }
            }
        } catch (Exception e) {
            System.out.println("Erro de conexão: " + e.getMessage());
        }
    }

    // --- MENU DE OPERAÇÕES ---
    private static void menuPrincipal() {
        System.out.println("\n--- MENU PRINCIPAL ---");
        System.out.println("1: Registar evento");
        System.out.println("2: Get quantidade");
        System.out.println("3: Get volume");
        System.out.println("4: Get avg price");
        System.out.println("5: Get max price");
        System.out.println("6: Wait simultaneous sales");
        System.out.println("7: Wait consecutive sales");
        System.out.println("8: Filtrar Eventos");
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
                    primaEnter();
                    break;

                case "3":
                    System.out.print("Produto: "); String p3 = sc.nextLine();
                    System.out.print("Dias atrás (1-D): "); int d3 = Integer.parseInt(sc.nextLine());
                    System.out.println("Volume Total: " + api.getVolume(p3, d3));
                    primaEnter();
                    break;

                case "4":
                    System.out.print("Produto: "); String p4 = sc.nextLine();
                    System.out.print("Dias atrás (1-D): "); int d4 = Integer.parseInt(sc.nextLine());
                    System.out.println("Preço Médio: " + api.getAvgPrice(p4, d4));
                    primaEnter();
                    break;

                case "5":
                    System.out.print("Produto: "); String p5 = sc.nextLine();
                    System.out.print("Dias atrás (1-D): "); int d5 = Integer.parseInt(sc.nextLine());
                    System.out.println("Preço Máximo: " + api.getMaxPrice(p5, d5));
                    primaEnter();
                    break;

                case "6":
                    System.out.print("Produto 1: "); String s1 = sc.nextLine();
                    System.out.print("Produto 2: "); String s2 = sc.nextLine();
                    System.out.println("Aguardando vendas simultâneas...");
                    System.out.println(api.waitSimultaneous(s1, s2));
                    primaEnter();
                    break;

                case "7":
                    System.out.print("Produto: "); String cp = sc.nextLine();
                    System.out.print("Número de vendas (N): "); int cn = Integer.parseInt(sc.nextLine());
                    System.out.println("Aguardando vendas consecutivas...");
                    System.out.println(api.waitConsecutive(cp, cn));
                    primaEnter();
                    break;

                case "8":
                    System.out.println("Escreva os produtos separados por vírgula (ex: Batatas,Cebolas):");
                    System.out.print("> ");
                    String inputProds = sc.nextLine();
                    List<String> listaProds = Arrays.asList(inputProds.replace(" ", "").split(","));

                    System.out.print("Consultar quantos dias atrás? (1-D): ");
                    int d8 = Integer.parseInt(sc.nextLine());

                    System.out.println("\nA pedir dados comprimidos ao servidor...");
                    System.out.println(api.filterEvents(listaProds, d8));

                    primaEnter();
                    break;

                case "0":
                    System.exit(0);
                    break;

                default:
                    System.out.println("Opção inválida.");
            }
        } catch (Exception e) {
            System.out.println("Erro na operação: " + e.getMessage());
            primaEnter();
        }
    }
}