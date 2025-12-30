
public class Stats {
    // Totais acumulados para UM produto em UM dia específico
    public final long quantidadeTotal;
    public final double volumeTotal;   // (Soma de: quantidade * preco)
    public final double precoMaximo;

    public Stats(long qtd, double vol, double max) {
        this.quantidadeTotal = qtd;
        this.volumeTotal = vol;
        this.precoMaximo = max;
    }

    public long getQuantidadeTotal() { return quantidadeTotal; }
    public double getVolumeTotal() { return volumeTotal; }
    public double getPrecoMaximo() { return precoMaximo; }
}