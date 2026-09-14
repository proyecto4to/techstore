package com.techstore.seguridad;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AccesoPantallaRolRepository extends JpaRepository<AccesoPantallaRol, Long> {

    List<AccesoPantallaRol> findByRolNombreOrderByPantallaAsc(String rolNombre);

    Optional<AccesoPantallaRol> findByRolNombreAndPantalla(String rolNombre, String pantalla);

    /** Las pantallas que alguien configuro alguna vez, para ofrecerlas sin inventarlas. */
    @Query("select distinct a.pantalla from AccesoPantallaRol a order by a.pantalla asc")
    List<String> pantallasConocidas();

    List<AccesoPantallaRol> findByRolNombreAndPermitidoTrueOrderByPantallaAsc(String rolNombre);
}
