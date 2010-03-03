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
 *  pixTrimTextComponents()
 *
 *      Input:  pixs (8 bpp)
 *              &pixa (input/output array of connected components)
 *      Return: text component image mask, or NULL on error
 *
 *  Notes:
 *      (1) This performs a two-pass rule-based trim on the array of
 *          connected components (CoCo's). For the following rules,
 *          let Wi, Hi be the width and height of a CoCo i. Let dX, dY
 *          be the distances between the centroids of two CoCo's.
 *      (2) The first pass applies the following to each CoCo:
 *          (a) 0.1 < Wi / Hi < 2
 *          (b) 20 < Wi * Hi
 *          (c) Wi > PixH/4
 *      (3) The second pass applies the following to every CoCo pair:
 *          (d) 0.5 < Hi / Hj < 2
 *          (e) dY < 0.2 * max(Hi, Hj)
 *          (f) dX < 2 * max(Wi, Wj)
 */
l_int32
pixTrimTextComponents(PIXA **ppixa)
{
l_int32   n, count;
l_int32  *remove;
PIXA     *pixa;

    PROCNAME("pixTrimTextComponents");

    pixa = *ppixa;

    n = pixaGetCount(pixa);
    remove = (l_int32 *) malloc(sizeof(l_int32) * n);

        /* First pass is rules 1-3 */
    count = removeInvalidComponents(pixa, remove);

        /* Second pass is rules 3-6 */
    count = removeInvalidPairs(pixa, remove);

        /* Perform clustering */
    count = clusterValidComponents(ppixa, remove);

    return 0;
}


l_int32
removeInvalidComponents(PIXA *pixa,
                        l_int32 *remove)
{
l_int32  n, count, i;
l_int32  x, y, w, h, r;

    PROCNAME("removeInvalidComponents");

    n = pixaGetCount(pixa);

    count = 0;

    for (i = 0; i < n; i++) {
        pixaGetBoxGeometry(pixa, i, &x, &y, &w, &h);

        r = (10 * w) / h;

        /* Ratio of width to height between 2 and 0.1
         * Height is less than half the image
         * Area is greater than 20 pixels */
        if (r > 20 || r < 1 || (w * h) < MIN_BLOB_AREA) {
            remove[i] = 1;
            count++;
        } else {
            remove[i] = 0;
        }
    }

    return count;
}

l_int32
removeInvalidPairs(PIXA *pixa,
                   l_int32 *remove)
{
l_int32    n, count, i, j;
l_int32    xi, yi, wi, hi, centerxi, centeryi;
l_int32    xj, yj, wj, hj, centerxj, centeryj;
l_int32    ratio_h, larger, distance;
l_int32   *marker;

    PROCNAME("removeInvalidPairs");

    n = pixaGetCount(pixa);
    marker = (l_int32 *) malloc(sizeof(l_int32) * n);

    count = 0;

    for (i = 0; i < n; i++) {
        if (remove[i]) continue;

        pixaGetBoxGeometry(pixa, i, &xi, &yi, &wi, &hi);
        centerxi = xi + wi / 2;
        centeryi = yi + hi / 2;

        marker[i] = 1;

        for (j = 0; marker[i] && j < n; j++) {
            if (i == j || remove[j]) continue;

            pixaGetBoxGeometry(pixa, j, &xj, &yj, &wj, &hj);

            /* Height ratio is between 0.5 and 2 */
            ratio_h = 2 * hi / hj;
            if (ratio_h > 4 || ratio_h < 1) continue;

            centerxj = xj + wj / 2;
            centeryj = yj + hj / 2;

            /* Horizontal distance between centers is
             * less than twice the wider character */
            larger = 2 * L_MAX(wi, wj);
            distance = L_ABS(centerxi - centerxj);
            if (distance > larger) continue;

            /* Vertical distance between centers is
               less than 50% of the taller character */
            larger = L_MAX(hi, hj);
            distance = 2 * L_ABS(centeryi - centeryj);
            if (distance > larger) continue;
            
            marker[i] = 0;
        }
    }

    for (i = 0; i < n; i++) {
        remove[i] |= marker[i];
    }

    free (marker);

    return count;
}

l_int32
clusterValidComponents(PIXA **ppixa,
                       l_int32 *remove)
{
l_int32     n, count, i, j, temp;
l_int32     x, y, w, h;
l_int32     xi, yi, wi, hi;
l_int32     xj, yj, wj, hj;
l_int32     dx, dy, d, mind, minj;
l_int32    *left, *right;
PIXA       *pixa, *pixad, *pixa_cluster;
PIX        *pix, *pixd;
BOX        *box, *boxd;

    PROCNAME("clusterValidComponents");

    pixa = *ppixa;

    n = pixaGetCount(pixa);
    left = (l_int32 *) malloc(sizeof(l_int32) * n);
    right = (l_int32 *) malloc(sizeof(l_int32) * n);

    for (i = 0; i < n; i++) {
        left[i] = -1;
        right[i] = -1;
    }

    for (i = 0; i < n; i++) {
        if (remove[i]) continue;

        pixaGetBoxGeometry(pixa, i, &xi, &yi, &wi, &hi);
        mind = -1;
        minj = -1;

        for (j = 0; j < n; j++) {
            if (i == j || remove[j]) continue;

            pixaGetBoxGeometry(pixa, j, &xj, &yj, &wj, &hj);

            /* i is left of j */
            if (xj < xi) continue;

            /* i shares at least half an edge with j */
            if (yj + hj / 2 < yi) continue;

            /* j shares at least hald an edge with i */
            if (yi + hi / 2 < yj) continue;

            /* j and i are similar widths */
            if (wi > wj && wi / wj > 5) continue;
            else if (wj > wi && wj / wi > 5) continue;

            /* j and i are similar heights */
            if (hi > hj && hi / hj > 5) continue;
            else if (hj > hi && hj / hi > 5) continue;

            w = 2 * L_MAX(wi, wj);

            /* j is within 2 widths of j */
            if (xj - (xi + wi) > w) continue;

            /* calculate distance between i and j */
            dx = xj - (xi + wi);
            dy = (yj + hj) - (yi + hi);
            d = dx * dx + dy * dy;
            
            if (mind < 0 || d < mind) {
                mind = d;
                minj = j;
            }
        }

        if (mind >= 0) {
            if ((j = left[minj]) >= 0) {
               right[j] = -1;
            }

            left[minj] = i;
            right[i] = minj;
        }
    }

    pixad = pixaCreate(0);
    count = 0;

    for (i = 0; i < n; i++) {
        if (remove[i]) continue;
        if (left[i] < 0 && right[i] < 0) continue;

        pixa_cluster = pixaCreate(1);
        pix = pixaGetPix(pixa, i, L_CLONE);
        box = pixaGetBox(pixa, i, L_CLONE);
        pixaAddPix(pixa_cluster, pix, L_INSERT);
        pixaAddBox(pixa_cluster, box, L_INSERT);

        boxGetGeometry(box, &xi, &yi, &wi, &hi);
        x = xi;
        y = yi;
        w = xi + wi;
        h = yi + hi;

        j = left[i];
        left[i] = -1;
        while (j >= 0) {
            pix = pixaGetPix(pixa, j, L_CLONE);
            box = pixaGetBox(pixa, j, L_CLONE);
            pixaAddPix(pixa_cluster, pix, L_INSERT);
            pixaAddBox(pixa_cluster, box, L_INSERT);

            boxGetGeometry(box, &xj, &yj, &wj, &hj);
            x = L_MIN(x, xj);
            y = L_MIN(y, yj);
            w = L_MAX(w, xj + wj);
            h = L_MAX(h, yj + hj);

            right[j] = -1;
            temp = left[j];
            left[j] = -1;
            j = temp;
        }

        j = right[i];
        right[i] = -1;
        while (j >= 0) {
            pix = pixaGetPix(pixa, j, L_CLONE);
            box = pixaGetBox(pixa, j, L_CLONE);
            pixaAddPix(pixa_cluster, pix, L_INSERT);
            pixaAddBox(pixa_cluster, box, L_INSERT);

            boxGetGeometry(box, &xj, &yj, &wj, &hj);
            x = L_MIN(x, xj);
            y = L_MIN(y, yj);
            w = L_MAX(w, xj + wj);
            h = L_MAX(h, yj + hj);

            left[j] = -1;
            temp = right[j];
            right[j] = -1;
            j = temp;
        }

        w = w - x;
        h = h - y;

        if (2 * w / h > 3 && pixaGetCount(pixa_cluster) >= CLUSTER_MIN_BLOBS) {
            pixd = pixCreate(w, h, 1);
            boxd = boxCreate(x, y, w, h);

            for (j = pixaGetCount(pixa_cluster) - 1; j >= 0; j--) {
                pix = pixaGetPix(pixa_cluster, j, L_CLONE);
                pixaGetBoxGeometry(pixa_cluster, j, &xj, &yj, &wj, &hj);
                pixRasterop(pixd, xj - x, yj - y, wj, hj, PIX_PAINT, pix, 0, 0);
                pixDestroy(&pix);
            }

            pixaAddPix(pixad, pixd, L_INSERT);
            pixaAddBox(pixad, boxd, L_INSERT);

            count++;
        }

        pixaDestroy(&pixa_cluster);
    }

    pixaDestroy(ppixa);
    *ppixa = pixad;

    return count;
}

l_int32
pixRemoveInnerBoxes(PIXA **ppixa)
{
PIXA    *pixa, *pixad;
PIX     *pixi;
BOXA    *boxa;
BOX     *boxi, *boxj;
l_int32 *marker;
l_int32  count, n, i, j, result;

    pixa = *ppixa;
    n = pixaGetCount(pixa);
    marker = (l_int32 *) calloc(n, sizeof(l_int32));
    count = n;

    for (i = 0; i < n - 1; i++) {
        boxi = pixaGetBox(pixa, i, L_CLONE);

        for (j = i + 1; !marker[i] && j < n; j++) {
            boxj = pixaGetBox(pixa, j, L_CLONE);

            if (!marker[j]) {
	        boxContains(boxj, boxi, &result);

                if (result) {
                    marker[i] = 1;
                    count--;
                } else {
                    boxContains(boxi, boxj, &result);

                    if (result) {
                        marker[j] = 1;
                        count--;
                    }
                }
            }

            boxDestroy(&boxj);
        }

        boxDestroy(&boxi);
    }

    pixad = pixaCreate(count);

    for (i = 0; i < n; i++) {
        if (!marker[i]) {
            pixi = pixaGetPix(pixa, i, L_CLONE);
            boxi = pixaGetBox(pixa, i, L_CLONE);
            pixaAddPix(pixad, pixi, L_INSERT);
            pixaAddBox(pixad, boxi, L_INSERT);
        }
    }

    free(marker);

    pixaDestroy(ppixa);
    *ppixa = pixad;

    return 0;
}
