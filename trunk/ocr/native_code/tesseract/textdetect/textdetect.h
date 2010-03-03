/* 
**
** Copyright 2009, Google Inc.
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

#ifndef TESSERACT_API_TEXTDETECT_H__
#define TESSERACT_API_TEXTDETECT_H__

/*------------------------------------------------------------------*
 *          Fisher's discriminant rate-based text detection         *
 *------------------------------------------------------------------*
 *  Based on the the method described in:
 *      Ezaki, N., Kiyota, K., Minh, B. T., Bulacu, M., and
 *          Schomaker, L. "Improved Text-Detection Methods for a
 *          Camera-based Text Reading System for Blind Persons".
 *          In Proceedings of the Eighth International Conference
 *          on Document Analysis and Recognition (August 31 -
 *          September 01, 2005). ICDAR. IEEE Computer Society,
 *          Washington, DC, 257-261.
 */

#include "allheaders.h"
#include <utils/Log.h>
#include <stdio.h>

    /* Text detect configuration */
#define   FDR_SX              32
#define   FDR_SY              32
#define   FDR_THRESH          3.5
#define   SCORE_FACT          0.01
#define   CONN_COMP           4
#define   SDCARD              "/sdcard/"
#define   MIN_BLOB_AREA       20
#define   CLUSTER_MIN_BLOBS   3


#ifdef __cplusplus
extern "C" {
#endif  /* __cplusplus */

l_int32
pixDetectText(PIX      *pixs,
              PIXA    **pixa,
              l_int32   debug);

l_int32
pixFisherAdaptiveThreshold(PIX       *pixs,
                           l_int32    sx,
                           l_int32    sy,
                           l_float32  scorefract,
                           l_float32  fdrthresh,
                           PIX      **ppixd,
                           PIX      **ppixdi);

l_int32
pixGetFisherThresh(PIX       *pixs,
                   l_float32  scorefract,
                   l_int32    factor,
                   l_float32 *pxfdr,
                   l_int32   *pxthresh);

l_int32
pixTrimTextComponents(PIXA **pixa);

l_int32
removeInvalidComponents(PIXA *pixa,
                        l_int32 *remove);

l_int32
removeInvalidPairs(PIXA *pixa,
                   l_int32 *remove);

l_int32
clusterValidComponents(PIXA **pixa,
                       l_int32 *remove);

l_int32
pixRemoveInnerBoxes(PIXA **boxa);

#ifdef __cplusplus
}
#endif  /* __cplusplus */

#endif  // TESSERACT_API_TEXTDETECT_H__
