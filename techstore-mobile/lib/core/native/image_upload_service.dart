import 'dart:io';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:image_cropper/image_cropper.dart';
import 'package:image_picker/image_picker.dart';

import '../network/api_client.dart';

enum ImageOrigin { camera, gallery }

final class ImageUploadService {
  ImageUploadService(this._api, {ImagePicker? picker, ImageCropper? cropper})
    : _picker = picker ?? ImagePicker(),
      _cropper = cropper ?? ImageCropper();

  static const maxBytes = 5 * 1024 * 1024;

  final ApiClient _api;
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
      throw StateError('La imagen supera 5 MB aun despues de comprimirla.');
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
