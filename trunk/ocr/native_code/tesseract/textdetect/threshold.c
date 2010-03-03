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

#include "textdetect.h"

/*!
 *  pixFisherAdaptiveThreshold()
 *
 *      Input:  pixs (8 bpp)
 *              sx, sy (desired tile dimensions; actual size may vary)
 *              scorefract (fraction of the max Otsu score; typ. 0.1)
 *              fdrthresh (threshold for Fisher's Discriminant Rate; typ. 5.0)
 *              &pixfdr (<optional return> array of Fisher's Discriminant
 *                       Rate values found for each tile)
 *              &pixd (<optional return> thresholded input pixs, based on
 *                     the FDR array)
 *      Return: 0 if OK, 1 on error
 */
l_int32
pixFisherAdaptiveThreshold(PIX       *pixs,
                           l_int32    sx,
                           l_int32    sy,
                           l_float32  scorefract,
                           l_float32  fdrthresh,
                           PIX      **ppixd,
                           PIX      **ppixdi)
{
l_float32   fdr, gfdr;
l_int32     w, h, d, nx, ny, x, y, thresh, gthresh;
l_uint32    val, ival;
PIX        *pixfdr, *pixfdri, *pixb, *pixd, *pixdi, *pixt;
PIXTILING  *pt;

    PROCNAME("pixFisherAdaptiveThreshold");

    if (!pixs)
        return ERROR_INT("pixs not defined", procName, 1);
    pixGetDimensions(pixs, &w, &h, &d);
    if (d != 8)
        return ERROR_INT("pixs not 8 bpp", procName, 1);
    if (sx < 8 || sy < 8)
        return ERROR_INT("sx and sy must be >= 8", procName, 1);

        /* Compute global Otsu threshold */
/*
    pixGetFisherThresh(pixs, scorefract, 8, &gfdr, &gthresh);
*/
        /* Compute FDR & threshold for individual tiles */
    nx = L_MAX(1, w / sx);
    ny = L_MAX(1, h / sy);
    pt = pixTilingCreate(pixs, nx, ny, 0, 0, 0, 0);
    pixfdr = pixCreate(nx, ny, 8);
    pixfdri = pixCreate(nx, ny, 8);
    for (y = 0; y < ny; y++) {
        for (x = 0; x < nx; x++) {
            pixt = pixTilingGetTile(pt, y, x);
            pixGetFisherThresh(pixt, scorefract, 1, &fdr, &thresh);

            if (fdr > fdrthresh) {
                val = thresh;
                ival = thresh;
            } else {
                val = 0;
                ival = 255;
            }

            pixSetPixel(pixfdr, x, y, val);
            pixSetPixel(pixfdri, x, y, ival);

            pixDestroy(&pixt);
        }
    }

    if (ppixd && ppixdi) {
        pixd = pixCreate(w, h, 1);
        pixdi = pixCreate(w, h, 1);

        for (y = 0; y < ny; y++) {
            for (x = 0; x < nx; x++) {
                pixt = pixTilingGetTile(pt, y, x);

                pixGetPixel(pixfdr, x, y, &val);
                pixb = pixThresholdToBinary(pixt, val);
                pixTilingPaintTile(pixd, y, x, pixb, pt);
                pixDestroy(&pixb);

                pixGetPixel(pixfdri, x, y, &val);
                pixb = pixThresholdToBinary(pixt, val);
                pixTilingPaintTile(pixdi, y, x, pixb, pt);
                pixDestroy(&pixb);

                pixDestroy(&pixt);
            }
        }

        pixInvert(pixdi, pixdi);

        *ppixd = pixd;
        *ppixdi = pixdi;
    }

    pixTilingDestroy(&pt);
    pixDestroy(&pixfdr);
    pixDestroy(&pixfdri);

    return 0;
}


/*!
 *  pixGetFisherThresh()
 *
 *      Input:  pixs (any depth; cmapped ok)
 *              scorefract (fraction of the max score, used to determine
 *                          the range over which the histogram min is searched)
 *              factor (subsampling factor; integer >= 1)
 *              &xfdr (<optional return> Fisher's Discriminate Rate value)
 *              &xthresh (<optional return> Otsu threshold value)
 *      Return: 0 if OK, 1 on error
 */
l_int32
pixGetFisherThresh(PIX       *pixs,
                   l_float32  scorefract,
                   l_int32    factor,
                   l_float32 *pfdr,
                   l_int32   *pthresh)
{
l_float32  mean, mean1, mean2, sum, sum1, sum2, fract;
l_float32  var, between, within, fdr, scale;
l_int32   thresh;
NUMA      *na;
PIX       *pixg;

    PROCNAME("pixGetFisherThresh");
  
    if (!pixs)
        return ERROR_INT("pixs not defined", procName, 1);

        /* Generate a subsampled 8 bpp version if needed */
    if (factor > 1) {
        scale = 1.0 / (l_float32)factor;
        pixg = pixScaleBySampling(pixs, scale, scale);
    } else {
        pixg = pixClone(pixs);
    }

    na = pixGetGrayHistogram(pixg, 1);

        /* Compute Otsu threshold for histogram */
    numaSplitDistribution(na, scorefract, &thresh, &mean1, &mean2,
                          &sum1, &sum2, NULL);

        /* Compute Fisher's Discriminant Rate if needed */
    if (pfdr) {
        numaGetHistogramStats(na, 0.0, 1.0, NULL, NULL, NULL, &var);
        numaGetSum(na, &sum);

            /* Between-class variance = sum of weighted squared distances
                                        between-class and overall means */
        fract = sum1 / sum;
        between = (fract * (1 - fract)) * (mean1 - mean2) * (mean1 - mean2);

            /* Within-class variance = difference between total variance
                                       and between-class variance */
        within = var - between;

            /* FDR = between-class variance over within-class variance */
        if (between == 0) {
          fdr = 0;
        } else if (within <= 1) {
          fdr = between;
        } else {
          fdr = between / within;
        }

        *pfdr = fdr;
    }

    if (pthresh)
        *pthresh = thresh;

    pixDestroy(&pixg);
    numaDestroy(&na);

    return 0;
}

