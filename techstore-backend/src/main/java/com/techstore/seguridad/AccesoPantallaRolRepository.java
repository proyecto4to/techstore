package com.techstore.seguridad;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccesoPantallaRolRepository extends JpaRepository<AccesoPantallaRol, Long> {

    List<AccesoPantallaRol> findByRolNombreOrderByPantallaAsc(String rolNombre);

    Optional<AccesoPantallaRol> findByRolNombreAndPantalla(String rolNombre, String pantalla);
}
