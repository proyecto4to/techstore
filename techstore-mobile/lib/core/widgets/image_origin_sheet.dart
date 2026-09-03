import 'package:flutter/material.dart';

import '../native/image_upload_service.dart';

/// Hoja compartida para elegir entre cámara y galería.
///
/// Centraliza el diálogo que antes estaba duplicado en cada pantalla, para que
/// tomar una foto se vea y se comporte igual en todo el sistema.
Future<ImageOrigin?> pickImageOrigin(
  BuildContext context, {
  String cameraLabel = 'Tomar foto',
  String galleryLabel = 'Elegir de la galería',
  IconData cameraIcon = Icons.photo_camera_outlined,
}) {
  return showModalBottomSheet<ImageOrigin>(
    context: context,
    builder: (context) => SafeArea(
      child: Wrap(
        children: [
          ListTile(
            leading: Icon(cameraIcon),
            title: Text(cameraLabel),
            onTap: () => Navigator.pop(context, ImageOrigin.camera),
          ),
          ListTile(
            leading: const Icon(Icons.photo_library_outlined),
            title: Text(galleryLabel),
            onTap: () => Navigator.pop(context, ImageOrigin.gallery),
          ),
        ],
      ),
    ),
  );
}
