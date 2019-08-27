import java.util.Objects;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Stream;
import java.util.stream.IntStream;
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
            .map(this::buildUTXO)
            .filter(utxo -> !utxoPool.contains(utxo))
            .findFirst()
            .isPresent();
        boolean areOutputValuesNonNegative = !tx.getOutputs().stream()
            .filter(o -> o.value < 0)
            .findFirst()
            .isPresent();
        double inputsValue = tx.getInputs().stream()
            .map(this::buildUTXO)
            .map(utxo -> utxoPool.getTxOutput(utxo))
            .filter(Objects::nonNull)
            .map(o -> o.value)
            .reduce(0.0, (x, y) -> x + y);
        double outputsValue = tx.getOutputs().stream()
            .map(o -> o.value)
            .reduce(0.0, (x, y) -> x + y);
        boolean areUTXOClaimedOnlyOnce = !tx.getInputs().stream()
            .map(this::buildUTXO)
            .collect(Collectors.groupingBy(UTXO::hashCode))
            .entrySet()
            .stream()
            .filter(x -> x.getValue().size() > 1)
            .findFirst()
            .isPresent();
        boolean areAllInputSignaturesValid = !IntStream.range(0, tx.getInputs().size())
            .filter(index -> {
                    Transaction.Input i = tx.getInputs().get(index);
                    Optional<Transaction.Output> output = Optional.ofNullable(utxoPool.getTxOutput(buildUTXO(i)));
                    if (output.isPresent())
                        return !Crypto.verifySignature(output.get().address, tx.getRawDataToSign(index), i.signature);
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
        List<Transaction> validTransactions = new ArrayList<Transaction>();
        Arrays.stream(possibleTxs).forEach(t -> {
                if (isValidTx(t)) {
                    handleTx(t);
                    validTransactions.add(t);
                }
            });
        return validTransactions.toArray(new Transaction[validTransactions.size()]);
    }

    private void handleTx(final Transaction tx) {
        tx.getInputs().stream()
            .map(this::buildUTXO)
            .forEach(utxo -> utxoPool.removeUTXO(utxo));
    }

    private UTXO buildUTXO(final Transaction.Input input) {
        return new UTXO(input.prevTxHash, input.outputIndex);
    }
}
