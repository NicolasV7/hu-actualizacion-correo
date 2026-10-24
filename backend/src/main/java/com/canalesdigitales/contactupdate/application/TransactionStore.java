package com.canalesdigitales.contactupdate.application;

import com.canalesdigitales.contactupdate.domain.UpdateTransaction;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Almacén de transacciones.
 * Simplificación deliberada: memoria de una sola instancia, sin expiración de registros; en producción se reemplaza
 * por una tabla con bloqueo optimista (docs/02 D-07) manteniendo esta interfaz.
 */
@Component
public class TransactionStore {

    private final Map<String, UpdateTransaction> transactions = new ConcurrentHashMap<>();

    public void save(UpdateTransaction transaction) {
        transactions.put(transaction.id(), transaction);
    }

    public Optional<UpdateTransaction> find(String transactionId) {
        return Optional.ofNullable(transactions.get(transactionId));
    }
}
