// Copyright (c) 2010 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

// clang-format off

#ifndef MEDIA_BASE_YUV_CONVERT_H_
#define MEDIA_BASE_YUV_CONVERT_H_

#include "ErrorList.h"
#include "chromium_types.h"
#include "mozilla/gfx/Types.h"

namespace mozilla {

namespace gfx {

// Type of YUV surface.
// The value of these enums matter as they are used to shift vertical indices.
enum YUVType {
  YV12 = 0,           // YV12 is half width and half height chroma channels.
  YV16 = 1,           // YV16 is half width and full height chroma channels.
  YV24 = 2,           // YV24 is full width and full height chroma channels.
  Y8 = 3              // Y8 is monochrome: no chroma channels.
};

// Mirror means flip the image horizontally, as in looking in a mirror.
// Rotate happens after mirroring.
enum Rotate {
  ROTATE_0,           // Rotation off.
  ROTATE_90,          // Rotate clockwise.
  ROTATE_180,         // Rotate upside down.
  ROTATE_270,         // Rotate counter clockwise.
  MIRROR_ROTATE_0,    // Mirror horizontally.
  MIRROR_ROTATE_90,   // Mirror then Rotate clockwise.
  MIRROR_ROTATE_180,  // Mirror vertically.
  MIRROR_ROTATE_270   // Transpose.
};

// Filter affects how scaling looks.
enum ScaleFilter {
  FILTER_NONE = 0,        // No filter (point sampled).
  FILTER_BILINEAR_H = 1,  // Bilinear horizontal filter.
  FILTER_BILINEAR_V = 2,  // Bilinear vertical filter.
  FILTER_BILINEAR = 3     // Bilinear filter.
};

nsresult ToNSResult(int aLibyuvResult);

enum RGB32Type {
  ARGB = 0,
  ABGR = 1
};
// Convert a frame of YUV to 32 bit ARGB or ABGR.
// Pass in YV16/YV12 depending on source format
nsresult
ConvertYCbCrToRGB32(const uint8_t* yplane,
                    const uint8_t* uplane,
                    const uint8_t* vplane,
                    uint8_t* rgbframe,
                    int pic_x,
                    int pic_y,
                    int pic_width,
                    int pic_height,
                    int ystride,
                    int uvstride,
                    int rgbstride,
                    YUVType yuv_type,
                    YUVColorSpace yuv_color_space,
                    ColorRange color_range,
                    RGB32Type rgb32_type);

nsresult
ConvertYCbCrToRGB32_deprecated(const uint8_t* yplane,
                               const uint8_t* uplane,
                               const uint8_t* vplane,
                               uint8_t* rgbframe,
                               int pic_x,
                               int pic_y,
                               int pic_width,
                               int pic_height,
                               int ystride,
                               int uvstride,
                               int rgbstride,
                               YUVType yuv_type,
                              RGB32Type rgb32_type);

// Scale a frame of YUV to 32 bit ARGB.
// Supports rotation and mirroring.
nsresult
ScaleYCbCrToRGB32(const uint8_t* yplane,
                  const uint8_t* uplane,
                  const uint8_t* vplane,
                  uint8_t* rgbframe,
                  int source_width,
                  int source_height,
                  int width,
                  int height,
                  int ystride,
                  int uvstride,
                  int rgbstride,
                  YUVType yuv_type,
                  YUVColorSpace yuv_color_space,
                  ScaleFilter filter);

nsresult
ScaleYCbCrToRGB32_deprecated(const uint8_t* yplane,
                             const uint8_t* uplane,
                             const uint8_t* vplane,
                             uint8_t* rgbframe,
                             int source_width,
                             int source_height,
                             int width,
                             int height,
                             int ystride,
                             int uvstride,
                             int rgbstride,
                             YUVType yuv_type,
                             Rotate view_rotate,
                             ScaleFilter filter);

nsresult
ConvertI420AlphaToARGB32(const uint8_t* yplane,
                         const uint8_t* uplane,
                         const uint8_t* vplane,
                         const uint8_t* aplane,
                         uint8_t* argbframe,
                         int pic_width,
                         int pic_height,
                         int yastride,
                         int uvstride,
                         int argbstride);

} // namespace gfx
} // namespace mozilla

#endif  // MEDIA_BASE_YUV_CONVERT_H_
