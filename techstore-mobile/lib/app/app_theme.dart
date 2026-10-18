import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../core/diseno/marca_tema.dart';
import '../core/diseno/medidas.dart';
import '../core/diseno/movimiento.dart';
import '../core/diseno/paleta.dart';
import '../core/diseno/tipografia.dart';

/// El tema de la app: oscuro y único, «Monokai Black & Blue».
///
/// No hay versión clara. El negro es la marca: una pantalla blanca entre dos
/// negras se lee como un error, no como una preferencia. Casi todas las
/// pantallas —también las de caja, reparto y administración— toman sus
/// colores de acá, así que un ajuste en este archivo llega a las cuarenta.
abstract final class AppTheme {
  static ThemeData marca() {
    const esquema = ColorScheme(
      brightness: Brightness.dark,
      // Primario claro sobre negro: es el color de los enlaces, el foco y lo
      // activo. El relleno de la acción principal es el azul eléctrico, que
      // va en el tema del botón porque es el que da 5,2:1 con texto blanco.
      primary: Paleta.azulLuminoso,
      onPrimary: Paleta.tinta,
      primaryContainer: Paleta.azulProfundo,
      onPrimaryContainer: Color(0xFFDBEAFE),
      secondary: Paleta.cian,
      onSecondary: Paleta.tinta,
      secondaryContainer: Paleta.azulProfundo,
      onSecondaryContainer: Color(0xFFDBEAFE),
      tertiary: Paleta.promocion,
      onTertiary: Color(0xFF1F1B00),
      tertiaryContainer: Paleta.promocionHundida,
      onTertiaryContainer: Color(0xFFF3EDB0),
      error: Paleta.error,
      onError: Color(0xFF1A0008),
      errorContainer: Paleta.errorHundido,
      onErrorContainer: Color(0xFFFFD6E0),
      surface: Paleta.fondo,
      onSurface: Paleta.texto,
      onSurfaceVariant: Paleta.textoSecundario,
      surfaceDim: Paleta.fondo,
      surfaceBright: Paleta.superficieElevada,
      surfaceContainerLowest: Paleta.fondo,
      surfaceContainerLow: Paleta.negroPrincipal,
      surfaceContainer: Paleta.negroAzulado,
      surfaceContainerHigh: Paleta.superficie,
      surfaceContainerHighest: Paleta.superficieElevada,
      outline: Paleta.bordeDeControl,
      outlineVariant: Paleta.borde,
      shadow: Colors.black,
      scrim: Colors.black,
      inverseSurface: Paleta.texto,
      onInverseSurface: Paleta.negroPrincipal,
      inversePrimary: Paleta.azulElectrico,
      // Sin tinte: Material aclara las superficies elevadas mezclándoles el
      // primario, y el negro dejaría de ser negro.
      surfaceTint: Colors.transparent,
    );

    final textos = Tipografia.escala();
    const filo = BorderSide(color: Paleta.borde);
    const contorno = BorderSide(color: Paleta.bordeDeControl);

    OutlineInputBorder bordeDeCampo(Color color, [double ancho = 1]) =>
        OutlineInputBorder(
          borderRadius: Medidas.bordeControl,
          borderSide: BorderSide(color: color, width: ancho),
        );

    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.dark,
      colorScheme: esquema,
      fontFamily: Tipografia.texto,
      textTheme: textos,
      scaffoldBackgroundColor: Paleta.fondo,
      canvasColor: Paleta.fondo,
      applyElevationOverlayColor: false,
      iconTheme: const IconThemeData(color: Paleta.texto, size: 24),
      extensions: const [MarcaTema.porDefecto],
      pageTransitionsTheme: const PageTransitionsTheme(
        builders: {
          TargetPlatform.android: TransicionDeMarca(),
          TargetPlatform.iOS: TransicionDeMarca(),
          TargetPlatform.linux: TransicionDeMarca(),
          TargetPlatform.macOS: TransicionDeMarca(),
          TargetPlatform.windows: TransicionDeMarca(),
          TargetPlatform.fuchsia: TransicionDeMarca(),
        },
      ),
      appBarTheme: AppBarThemeData(
        // Negro al tope; cuando el contenido pasa por debajo, sube un nivel
        // para separarse sin sombra, que sobre negro no se vería.
        backgroundColor: WidgetStateColor.resolveWith(
          (estados) => estados.contains(WidgetState.scrolledUnder)
              ? Paleta.negroPrincipal
              : Paleta.fondo,
        ),
        foregroundColor: Paleta.texto,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        scrolledUnderElevation: 0,
        centerTitle: false,
        titleSpacing: 20,
        titleTextStyle: textos.titleLarge,
        systemOverlayStyle: const SystemUiOverlayStyle(
          statusBarColor: Colors.transparent,
          statusBarIconBrightness: Brightness.light,
          statusBarBrightness: Brightness.dark,
          systemNavigationBarColor: Paleta.fondo,
          systemNavigationBarIconBrightness: Brightness.light,
        ),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: ButtonStyle(
          backgroundColor: WidgetStateProperty.resolveWith(
            (e) => e.contains(WidgetState.disabled)
                ? Paleta.superficie
                : Paleta.azulElectrico,
          ),
          foregroundColor: WidgetStateProperty.resolveWith(
            (e) => e.contains(WidgetState.disabled)
                ? Paleta.textoSecundario
                : Colors.white,
          ),
          iconColor: WidgetStateProperty.resolveWith(
            (e) => e.contains(WidgetState.disabled)
                ? Paleta.textoSecundario
                : Colors.white,
          ),
          overlayColor: WidgetStateProperty.resolveWith(
            (e) => e.contains(WidgetState.pressed)
                ? Colors.white.withValues(alpha: 0.14)
                : e.contains(WidgetState.hovered) ||
                      e.contains(WidgetState.focused)
                ? Colors.white.withValues(alpha: 0.08)
                : null,
          ),
          // La luz azul debajo de la acción principal: sobre negro es lo que
          // la levanta del fondo. Al apretar, se apaga un poco.
          shadowColor: const WidgetStatePropertyAll(Paleta.azulElectrico),
          elevation: WidgetStateProperty.resolveWith(
            (e) => e.contains(WidgetState.disabled)
                ? 0
                : e.contains(WidgetState.pressed)
                ? 1
                : 5,
          ),
          minimumSize: const WidgetStatePropertyAll(Size(64, 52)),
          padding: const WidgetStatePropertyAll(
            EdgeInsets.symmetric(horizontal: 22, vertical: 14),
          ),
          shape: const WidgetStatePropertyAll(
            RoundedRectangleBorder(borderRadius: Medidas.bordeControl),
          ),
          textStyle: WidgetStatePropertyAll(textos.labelLarge),
          animationDuration: Movimiento.corto,
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: ButtonStyle(
          foregroundColor: WidgetStateProperty.resolveWith(
            (e) => e.contains(WidgetState.disabled)
                ? Paleta.textoSecundario
                : Paleta.texto,
          ),
          side: WidgetStateProperty.resolveWith(
            (e) => e.contains(WidgetState.pressed) ||
                    e.contains(WidgetState.focused)
                ? const BorderSide(color: Paleta.azulPrincipal, width: 1.4)
                : contorno,
          ),
          overlayColor: WidgetStatePropertyAll(
            Paleta.azulPrincipal.withValues(alpha: 0.1),
          ),
          minimumSize: const WidgetStatePropertyAll(Size(64, 50)),
          padding: const WidgetStatePropertyAll(
            EdgeInsets.symmetric(horizontal: 20, vertical: 13),
          ),
          shape: const WidgetStatePropertyAll(
            RoundedRectangleBorder(borderRadius: Medidas.bordeControl),
          ),
          textStyle: WidgetStatePropertyAll(textos.labelLarge),
        ),
      ),
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(
          foregroundColor: Paleta.azulLuminoso,
          textStyle: textos.labelLarge,
          minimumSize: const Size(48, 48),
          shape: const RoundedRectangleBorder(
            borderRadius: Medidas.bordeControl,
          ),
        ),
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: Paleta.superficie,
          foregroundColor: Paleta.texto,
          minimumSize: const Size(64, 50),
          shape: const RoundedRectangleBorder(
            borderRadius: Medidas.bordeControl,
            side: filo,
          ),
          textStyle: textos.labelLarge,
        ),
      ),
      floatingActionButtonTheme: const FloatingActionButtonThemeData(
        backgroundColor: Paleta.azulElectrico,
        foregroundColor: Colors.white,
        elevation: 8,
        focusElevation: 8,
        hoverElevation: 10,
        highlightElevation: 3,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.all(Radius.circular(18)),
        ),
      ),
      inputDecorationTheme: InputDecorationThemeData(
        filled: true,
        fillColor: Paleta.negroAzulado,
        contentPadding: const EdgeInsets.symmetric(
          horizontal: 16,
          vertical: 16,
        ),
        border: bordeDeCampo(Paleta.bordeDeControl),
        enabledBorder: bordeDeCampo(Paleta.bordeDeControl),
        focusedBorder: bordeDeCampo(Paleta.azulPrincipal, 1.6),
        errorBorder: bordeDeCampo(Paleta.error),
        focusedErrorBorder: bordeDeCampo(Paleta.error, 1.6),
        disabledBorder: bordeDeCampo(Paleta.borde),
        labelStyle: textos.bodyMedium?.copyWith(color: Paleta.textoSecundario),
        floatingLabelStyle: WidgetStateTextStyle.resolveWith(
          (e) => textos.bodyMedium!.copyWith(
            color: e.contains(WidgetState.error)
                ? Paleta.error
                : e.contains(WidgetState.focused)
                ? Paleta.azulLuminoso
                : Paleta.textoSecundario,
          ),
        ),
        hintStyle: textos.bodyMedium?.copyWith(
          color: Paleta.textoSecundario.withValues(alpha: 0.75),
        ),
        helperStyle: textos.bodySmall,
        errorStyle: textos.bodySmall?.copyWith(color: Paleta.error),
        prefixIconColor: WidgetStateColor.resolveWith(
          (e) => e.contains(WidgetState.focused)
              ? Paleta.azulLuminoso
              : Paleta.textoSecundario,
        ),
        suffixIconColor: Paleta.textoSecundario,
        // Por defecto el error y la ayuda ocupan una sola línea y Flutter las
        // corta con puntos suspensivos. En el teléfono «Escribí un correo
        // válido, como nombre@correo.com» se leía «…como nombre…», que corta
        // justo el ejemplo que resuelve la duda.
        errorMaxLines: 3,
        helperMaxLines: 3,
      ),
      textSelectionTheme: TextSelectionThemeData(
        // El cursor de escritura es, literalmente, el cursor del isotipo.
        cursorColor: Paleta.cian,
        selectionColor: Paleta.azulElectrico.withValues(alpha: 0.45),
        selectionHandleColor: Paleta.cian,
      ),
      cardTheme: const CardThemeData(
        color: Paleta.negroAzulado,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        margin: EdgeInsets.zero,
        shape: RoundedRectangleBorder(
          borderRadius: Medidas.bordeTarjeta,
          side: filo,
        ),
      ),
      listTileTheme: ListTileThemeData(
        iconColor: Paleta.textoSecundario,
        textColor: Paleta.texto,
        titleTextStyle: textos.titleSmall,
        subtitleTextStyle: textos.bodySmall,
        contentPadding: const EdgeInsets.symmetric(horizontal: 16),
        shape: const RoundedRectangleBorder(
          borderRadius: Medidas.bordeControl,
        ),
      ),
      dialogTheme: DialogThemeData(
        backgroundColor: Paleta.superficie,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        barrierColor: Colors.black.withValues(alpha: 0.72),
        shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.all(Radius.circular(Medidas.radioHoja)),
          side: filo,
        ),
        titleTextStyle: textos.headlineSmall,
        contentTextStyle: textos.bodyMedium?.copyWith(
          color: Paleta.textoSecundario,
        ),
      ),
      bottomSheetTheme: BottomSheetThemeData(
        backgroundColor: Paleta.negroAzulado,
        modalBackgroundColor: Paleta.negroAzulado,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        modalBarrierColor: Colors.black.withValues(alpha: 0.7),
        showDragHandle: true,
        dragHandleColor: Paleta.bordeDeControl,
        shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(
            top: Radius.circular(Medidas.radioHoja),
          ),
          side: filo,
        ),
      ),
      snackBarTheme: SnackBarThemeData(
        behavior: SnackBarBehavior.floating,
        backgroundColor: Paleta.superficieElevada,
        contentTextStyle: textos.bodyMedium,
        actionTextColor: Paleta.cian,
        closeIconColor: Paleta.textoSecundario,
        elevation: 0,
        insetPadding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
        shape: const RoundedRectangleBorder(
          borderRadius: Medidas.bordeControl,
          side: filo,
        ),
      ),
      chipTheme: ChipThemeData(
        backgroundColor: Paleta.negroAzulado,
        selectedColor: Paleta.azulProfundo,
        disabledColor: Paleta.negroPrincipal,
        checkmarkColor: Paleta.azulLuminoso,
        labelStyle: textos.labelMedium?.copyWith(color: Paleta.texto),
        secondaryLabelStyle: textos.labelMedium?.copyWith(
          color: Paleta.azulLuminoso,
        ),
        side: WidgetStateBorderSide.resolveWith(
          (e) => e.contains(WidgetState.selected)
              ? const BorderSide(color: Paleta.azulPrincipal)
              : filo,
        ),
        shape: const StadiumBorder(),
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
        iconTheme: const IconThemeData(
          color: Paleta.textoSecundario,
          size: 18,
        ),
      ),
      navigationBarTheme: NavigationBarThemeData(
        backgroundColor: Paleta.negroPrincipal,
        surfaceTintColor: Colors.transparent,
        indicatorColor: Paleta.azulProfundo,
        elevation: 0,
        labelTextStyle: WidgetStateProperty.resolveWith(
          (e) => textos.labelSmall?.copyWith(
            color: e.contains(WidgetState.selected)
                ? Paleta.azulLuminoso
                : Paleta.textoSecundario,
          ),
        ),
        iconTheme: WidgetStateProperty.resolveWith(
          (e) => IconThemeData(
            color: e.contains(WidgetState.selected)
                ? Paleta.azulLuminoso
                : Paleta.textoSecundario,
          ),
        ),
      ),
      tabBarTheme: TabBarThemeData(
        indicatorColor: Paleta.azulPrincipal,
        labelColor: Paleta.texto,
        unselectedLabelColor: Paleta.textoSecundario,
        labelStyle: textos.labelLarge,
        unselectedLabelStyle: textos.labelLarge,
        dividerColor: Paleta.borde,
        indicatorSize: TabBarIndicatorSize.label,
      ),
      switchTheme: SwitchThemeData(
        thumbColor: WidgetStateProperty.resolveWith(
          (e) => e.contains(WidgetState.selected)
              ? Colors.white
              : Paleta.textoSecundario,
        ),
        trackColor: WidgetStateProperty.resolveWith(
          (e) => e.contains(WidgetState.selected)
              ? Paleta.azulElectrico
              : Paleta.superficie,
        ),
        trackOutlineColor: WidgetStateProperty.resolveWith(
          (e) => e.contains(WidgetState.selected)
              ? Paleta.azulElectrico
              : Paleta.bordeDeControl,
        ),
      ),
      checkboxTheme: CheckboxThemeData(
        fillColor: WidgetStateProperty.resolveWith(
          (e) => e.contains(WidgetState.selected)
              ? Paleta.azulElectrico
              : Colors.transparent,
        ),
        checkColor: const WidgetStatePropertyAll(Colors.white),
        side: const BorderSide(color: Paleta.bordeDeControl, width: 1.6),
        shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.all(Radius.circular(5)),
        ),
      ),
      radioTheme: RadioThemeData(
        fillColor: WidgetStateProperty.resolveWith(
          (e) => e.contains(WidgetState.selected)
              ? Paleta.azulLuminoso
              : Paleta.bordeDeControl,
        ),
      ),
      segmentedButtonTheme: SegmentedButtonThemeData(
        style: ButtonStyle(
          backgroundColor: WidgetStateProperty.resolveWith(
            (e) => e.contains(WidgetState.selected)
                ? Paleta.azulProfundo
                : Colors.transparent,
          ),
          foregroundColor: WidgetStateProperty.resolveWith(
            (e) => e.contains(WidgetState.selected)
                ? Paleta.azulLuminoso
                : Paleta.texto,
          ),
          side: const WidgetStatePropertyAll(contorno),
          textStyle: WidgetStatePropertyAll(textos.labelMedium),
        ),
      ),
      sliderTheme: const SliderThemeData(
        activeTrackColor: Paleta.azulPrincipal,
        inactiveTrackColor: Paleta.superficie,
        thumbColor: Paleta.azulLuminoso,
      ),
      progressIndicatorTheme: const ProgressIndicatorThemeData(
        color: Paleta.azulPrincipal,
        linearTrackColor: Paleta.superficie,
        circularTrackColor: Colors.transparent,
        refreshBackgroundColor: Paleta.superficie,
      ),
      dividerTheme: const DividerThemeData(
        color: Paleta.borde,
        thickness: 1,
        space: 1,
      ),
      badgeTheme: BadgeThemeData(
        // Los contadores van en el color del cursor.
        backgroundColor: Paleta.cian,
        textColor: Paleta.tinta,
        textStyle: textos.labelSmall?.copyWith(fontWeight: FontWeight.w700),
      ),
      tooltipTheme: TooltipThemeData(
        decoration: BoxDecoration(
          color: Paleta.superficieElevada,
          borderRadius: BorderRadius.circular(Medidas.radioChico),
          border: Border.all(color: Paleta.borde),
        ),
        textStyle: textos.bodySmall?.copyWith(color: Paleta.texto),
        waitDuration: const Duration(milliseconds: 400),
      ),
      popupMenuTheme: PopupMenuThemeData(
        color: Paleta.superficie,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        textStyle: textos.bodyMedium,
        shape: const RoundedRectangleBorder(
          borderRadius: Medidas.bordeControl,
          side: filo,
        ),
      ),
      menuTheme: const MenuThemeData(
        style: MenuStyle(
          backgroundColor: WidgetStatePropertyAll(Paleta.superficie),
          surfaceTintColor: WidgetStatePropertyAll(Colors.transparent),
          elevation: WidgetStatePropertyAll(0),
          side: WidgetStatePropertyAll(filo),
          shape: WidgetStatePropertyAll(
            RoundedRectangleBorder(borderRadius: Medidas.bordeControl),
          ),
        ),
      ),
      dropdownMenuTheme: const DropdownMenuThemeData(
        menuStyle: MenuStyle(
          backgroundColor: WidgetStatePropertyAll(Paleta.superficie),
          surfaceTintColor: WidgetStatePropertyAll(Colors.transparent),
          side: WidgetStatePropertyAll(filo),
        ),
      ),
      searchBarTheme: SearchBarThemeData(
        backgroundColor: const WidgetStatePropertyAll(Paleta.negroAzulado),
        surfaceTintColor: const WidgetStatePropertyAll(Colors.transparent),
        elevation: const WidgetStatePropertyAll(0),
        side: WidgetStateProperty.resolveWith(
          (e) => e.contains(WidgetState.focused)
              ? const BorderSide(color: Paleta.azulPrincipal, width: 1.4)
              : contorno,
        ),
        shape: const WidgetStatePropertyAll(
          RoundedRectangleBorder(borderRadius: Medidas.bordeControl),
        ),
        hintStyle: WidgetStatePropertyAll(
          textos.bodyLarge?.copyWith(color: Paleta.textoSecundario),
        ),
        textStyle: WidgetStatePropertyAll(textos.bodyLarge),
        padding: const WidgetStatePropertyAll(
          EdgeInsets.symmetric(horizontal: 14),
        ),
        constraints: const BoxConstraints(minHeight: 54),
      ),
      expansionTileTheme: const ExpansionTileThemeData(
        iconColor: Paleta.azulLuminoso,
        collapsedIconColor: Paleta.textoSecundario,
        textColor: Paleta.texto,
        collapsedTextColor: Paleta.texto,
        shape: Border(),
        collapsedShape: Border(),
      ),
      datePickerTheme: DatePickerThemeData(
        backgroundColor: Paleta.superficie,
        surfaceTintColor: Colors.transparent,
        headerBackgroundColor: Paleta.negroAzulado,
        headerForegroundColor: Paleta.texto,
        dividerColor: Paleta.borde,
        shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.all(Radius.circular(Medidas.radioHoja)),
          side: filo,
        ),
        todayBorder: const BorderSide(color: Paleta.cian),
        dayStyle: textos.bodyMedium,
      ),
      timePickerTheme: const TimePickerThemeData(
        backgroundColor: Paleta.superficie,
        dialBackgroundColor: Paleta.negroAzulado,
        hourMinuteColor: Paleta.negroAzulado,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.all(Radius.circular(Medidas.radioHoja)),
          side: filo,
        ),
      ),
      scrollbarTheme: ScrollbarThemeData(
        thumbColor: WidgetStatePropertyAll(
          Paleta.bordeDeControl.withValues(alpha: 0.8),
        ),
        radius: const Radius.circular(8),
        thickness: const WidgetStatePropertyAll(4),
      ),
    );
  }
}
