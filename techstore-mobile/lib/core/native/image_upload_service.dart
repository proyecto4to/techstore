import 'dart:io';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:image_cropper/image_cropper.dart';
import 'package:image_picker/image_picker.dart';

import '../errors/app_failure.dart';
import '../network/api_client.dart';
import 'app_excursion.dart';

enum ImageOrigin { camera, gallery }

final class ImageUploadService {
  ImageUploadService(
    this._api, {
    AppExcursion? excursion,
    ImagePicker? picker,
    ImageCropper? cropper,
  }) : _excursion = excursion ?? AppExcursion(),
       _picker = picker ?? ImagePicker(),
       _cropper = cropper ?? ImageCropper();

  static const maxBytes = 5 * 1024 * 1024;

  final ApiClient _api;

  /// Camara, galeria y recorte mandan la app a segundo plano. Sin marcarlo, el
  /// cierre de sesion por segundo plano se llevaria puesta la operacion.
  final AppExcursion _excursion;
  final ImagePicker _picker;
  final ImageCropper _cropper;

  Future<bool> pickAndUploadProduct(int productId, ImageOrigin origin) async {
    return _pickAndUpload(
      origin: origin,
      title: 'Recortar producto',
      filename: 'producto-$productId.jpg',
      endpoint: '/api/v1/productos/$productId/imagen',
    );
  }

  Future<bool> pickAndUploadPurchaseReceipt(
    int purchaseId,
    ImageOrigin origin,
  ) async {
    return _pickAndUpload(
      origin: origin,
      title: 'Recortar comprobante',
      filename: 'comprobante-compra-$purchaseId.jpg',
      endpoint: '/api/v1/compras/$purchaseId/comprobante/archivo',
    );
  }

  Future<bool> _pickAndUpload({
    required ImageOrigin origin,
    required String title,
    required String filename,
    required String endpoint,
  }) async {
    return _excursion.durante(
      () => _elegirRecortarYSubir(
        origin: origin,
        title: title,
        filename: filename,
        endpoint: endpoint,
      ),
    );
  }

  Future<bool> _elegirRecortarYSubir({
    required ImageOrigin origin,
    required String title,
    required String filename,
    required String endpoint,
  }) async {
    final selected = await _picker.pickImage(
      source: origin == ImageOrigin.camera
          ? ImageSource.camera
          : ImageSource.gallery,
      imageQuality: 95,
    );
    if (selected == null) return false;
    final cropped = await _cropper.cropImage(
      sourcePath: selected.path,
      compressFormat: ImageCompressFormat.jpg,
      compressQuality: 82,
      maxWidth: 1600,
      maxHeight: 1600,
      uiSettings: [
        AndroidUiSettings(
          toolbarTitle: title,
          toolbarColor: const Color(0xFF1F2937),
          toolbarWidgetColor: Colors.white,
          lockAspectRatio: false,
        ),
      ],
    );
    if (cropped == null) return false;

    final prepared = File(cropped.path);
    if (await prepared.length() > maxBytes) {
      // Era un StateError: al usuario le llegaba el texto de una excepcion
      // tecnica. Ahora es una falla de validacion, que la interfaz ya sabe
      // mostrar como mensaje comun.
      throw const ValidationFailure(
        'La foto sigue pesando mas de 5 MB despues de comprimirla. '
        'Probá con una toma mas chica o recortá una parte.',
      );
    }
    await _api.post<Map<String, Object?>>(
      endpoint,
      data: FormData.fromMap({
        'archivo': await MultipartFile.fromFile(
          prepared.path,
          filename: filename,
        ),
      }),
    );
    return true;
  }
}
