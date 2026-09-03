package com.techstore.reparto;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CierreCajaRepartidorRepository extends JpaRepository<CierreCajaRepartidor, Long> {
    boolean existsByRepartidorIdAndEstado(Long repartidorId, EstadoTransferencia estado);
}
