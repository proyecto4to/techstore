package com.techstore.service;

import com.techstore.entity.Autonumerador;
import com.techstore.exception.BusinessException;
import com.techstore.repository.AutonumeradorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AutonumeradorService {

    private final AutonumeradorRepository autonumeradorRepository;

    /**
     * Devuelve el siguiente número de la secuencia con bloqueo pesimista.
     * Debe invocarse dentro de la transacción del llamador para que el
     * número solo se consuma si la operación completa se confirma.
     */
    @Transactional
    public int siguiente(String entidad) {
        Autonumerador contador = autonumeradorRepository.findByEntidadForUpdate(entidad)
                .orElseThrow(() -> new BusinessException("No existe autonumerador para la entidad " + entidad));
        contador.setValor(contador.getValor() + 1);
        autonumeradorRepository.save(contador);
        return contador.getValor();
    }
}
