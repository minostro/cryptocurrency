import java.util.Objects;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.util.Optional;

public class TxHandler {
    private UTXOPool utxoPool;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
        this.utxoPool = new UTXOPool(utxoPool);
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool,
     * (2) the signatures on each input of {@code tx} are valid,
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        boolean areInputsInUTXOPool = !tx.getInputs().stream()
            .filter(i -> !utxoPool.contains(new UTXO(i.prevTxHash, i.outputIndex)))
            .findFirst()
            .isPresent();
        boolean areOutputValuesNonNegative = !tx.getOutputs().stream()
            .filter(o -> o.value < 0)
            .findFirst()
            .isPresent();
        double inputsValue = tx.getInputs().stream()
            .map(i -> utxoPool.getTxOutput(new UTXO(i.prevTxHash, i.outputIndex)))
            .filter(Objects::nonNull)
            .map(o -> o.value)
            .reduce(0.0, (x, y) -> x + y);
        double outputsValue = tx.getOutputs().stream()
            .map(o -> o.value)
            .reduce(0.0, (x, y) -> x + y);
        boolean areUTXOClaimedOnlyOnce = !tx.getInputs().stream()
            .map(i -> new UTXO(i.prevTxHash, i.outputIndex))
            .collect(Collectors.groupingBy(UTXO::hashCode))
            .entrySet()
            .stream()
            .filter(x -> x.getValue().size() > 1)
            .findFirst()
            .isPresent();
        boolean areAllInputSignaturesValid = !tx.getInputs().stream()
            .filter(i -> {
                    Optional<Transaction.Output> output = Optional.ofNullable(utxoPool.getTxOutput(new UTXO(i.prevTxHash, i.outputIndex)));
                    if (output.isPresent())
                        return !Crypto.verifySignature(output.get().address, tx.getRawDataToSign(i.outputIndex), i.signature);
                    else
                        return false;
                })
            .findFirst()
            .isPresent();
        if (!areInputsInUTXOPool || !areOutputValuesNonNegative || outputsValue > inputsValue || !areUTXOClaimedOnlyOnce || !areAllInputSignaturesValid)
            return false;
        return true;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        List<Transaction> validTransactions = Arrays.stream(possibleTxs).filter(this::isValidTx).collect(Collectors.toList());
        validTransactions.stream()
            .flatMap(t -> t.getInputs().stream())
            .forEach(i -> utxoPool.removeUTXO(new UTXO(i.prevTxHash, i.outputIndex)));
        return validTransactions.toArray(new Transaction[validTransactions.size()]);
    }

}
