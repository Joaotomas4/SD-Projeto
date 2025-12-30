public class OpCode {
    // Autenticação e Registo
    public static final int REGISTER = 1;
    public static final int LOGIN = 2;

    // Escrita
    public static final int ADD_EVENT = 3;

    // Consultas de Agregação
    public static final int GET_QUANTITY = 4;
    public static final int GET_VOLUME = 5;
    public static final int GET_AVG_PRICE = 6;
    public static final int GET_MAX_PRICE = 7;

    // Consultas Complexas
    public static final int FILTER_EVENTS = 8;
    public static final int SIMULTANEOUS_SALES = 9;
    public static final int CONSECUTIVE_SALES = 10;

    // Respostas do Servidor
    public static final int OK = 200;
    public static final int ERROR = 255;
}