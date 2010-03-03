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

#include "allheaders.h"
#include "textdetect.h"
#include "malloc.h"
#include <time.h>

/*!
 *  pixDetectText()
 *
 *      Input:  pixs (8 bpp)
 *              &pixd (<optional return> masked text-only image)
 *              &pixa (<optional return> array of text components)
 *      Return: 0 if OK, 1 on error
 *
 *  Notes:
 *      (1) See the paper referenced in textdetect.h for more details on
 *          this text-detection algorithm.
 */
l_int32
pixDetectText(PIX *pixs, PIXA **ppixa, l_int32 debug)
{
PIX         *pixtemp;
PIX         *pixth, *pixthi;
PIXA        *pixa, *pixai;
BOXA        *boxa, *boxai;
l_int32      w, h, d;
l_int32      t, count;
char         file[255];

    PROCNAME("pixDetectText");

    if (!pixs)
        return ERROR_INT("pixs not defined", procName, 1);
    pixGetDimensions(pixs, &w, &h, &d);
    if (d != 8)
        return ERROR_INT("pixs not 8 bpp", procName, 1);

    t = (l_int32)(time(NULL) & 0xFFFFFFFF);

    if (debug) {
        sprintf(file, SDCARD "%d_pix_input.bmp", t);
        pixWrite(file, pixs, IFF_BMP);
    }

    if (pixFisherAdaptiveThreshold(pixs, FDR_SX, FDR_SY, SCORE_FACT,
                                   FDR_THRESH, &pixth, &pixthi))
        return ERROR_INT("thresholding failed", procName, 1);
    if (debug) {
        sprintf(file, SDCARD "%d_pix_threshold.bmp", t);
        pixWrite(file, pixth, IFF_BMP);

        sprintf(file, SDCARD "%d_pix_thresholdi.bmp", t);
        pixWrite(file, pixthi, IFF_BMP);
    }

    if ((boxa = pixConnCompPixa(pixth, &pixa, CONN_COMP)) == NULL)
        return ERROR_INT("component extraction failed", procName, 1);
    boxaDestroy(&boxa);
    pixDestroy(&pixth);

    if ((boxai = pixConnCompPixa(pixthi, &pixai, CONN_COMP)) == NULL)
        return ERROR_INT("inverted extraction failed", procName, 1);
    boxaDestroy(&boxai);
    pixDestroy(&pixthi);

    if (debug && pixaGetCount(pixa) > 0) {
        pixtemp = pixaDisplayRandomCmap(pixa, w, h);
        sprintf(file, SDCARD "%d_pix_threshold_cc.bmp", t);
        pixWrite(file, pixtemp, IFF_BMP);
        pixDestroy(&pixtemp);
    }

    if (debug && pixaGetCount(pixai) > 0) {
        pixtemp = pixaDisplayRandomCmap(pixai, w, h);
        sprintf(file, SDCARD "%d_pix_thresholdi_cc.bmp", t);
        pixWrite(file, pixtemp, IFF_BMP);
        pixDestroy(&pixtemp);
    }

    if (pixTrimTextComponents(&pixa))
        return ERROR_INT("component trimming failed", procName, 1);

    if (pixTrimTextComponents(&pixai))
        return ERROR_INT("inverted trimming failed", procName, 1);

    if (debug && pixaGetCount(pixa) > 0) {
        pixtemp = pixaDisplayRandomCmap(pixa, w, h);
        sprintf(file, SDCARD "%d_pix_threshold_trim.bmp", t);
        pixWrite(file, pixtemp, IFF_BMP);
        pixDestroy(&pixtemp);
    }

    if (debug && pixaGetCount(pixai) > 0) {
        pixtemp = pixaDisplayRandomCmap(pixai, w, h);
        sprintf(file, SDCARD "%d_pix_thresholdi_trim.bmp", t);
        pixWrite(file, pixtemp, IFF_BMP);
        pixDestroy(&pixtemp);
    }

    pixaJoin(pixa, pixai, 0, 0);
    pixaDestroy(&pixai);

/*
    if (pixRemoveInnerBoxes(&pixa))
        return ERROR_INT("removing inner boxes failed", procName, 1);
*/

    *ppixa = pixa;

    return 0;
}

