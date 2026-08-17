package com.techstore.envios;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TarifaEnvioRepository extends JpaRepository<TarifaEnvio, Long> {
    List<TarifaEnvio> findByActivaTrueOrderByPrioridadAscIdAsc();
}
