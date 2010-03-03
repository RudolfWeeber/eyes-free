LOCAL_PATH:= $(call my-dir)

#
# liblept (native)
#

include $(CLEAR_VARS)

LOCAL_SRC_FILES=		\
	adaptmap.c		\
	affine.c		\
	affinecompose.c		\
	arithlow.c		\
	arrayaccess.c		\
	bardecode.c		\
	baseline.c		\
	bbuffer.c		\
	bilinear.c		\
	binarize.c		\
	binexpand.c		\
	binexpandlow.c		\
	binreduce.c		\
	binreducelow.c		\
	blend.c			\
	bmf.c			\
	bmpio.c			\
	bmpiostub.c		\
	boxbasic.c		\
	boxfunc1.c		\
	boxfunc2.c		\
	boxfunc3.c		\
	ccbord.c		\
	ccthin.c		\
	classapp.c		\
	colorcontent.c		\
	colormap.c		\
	colormorph.c		\
	colorquant1.c		\
	colorquant2.c		\
	colorseg.c		\
	compare.c		\
	conncomp.c		\
	convolve.c		\
	convolvelow.c		\
	correlscore.c		\
	dwacomb.2.c		\
	dwacomblow.2.c		\
	edge.c			\
	endiantest.c		\
	enhance.c		\
	fhmtauto.c		\
	fhmtgen.1.c		\
	fhmtgenlow.1.c		\
	flipdetect.c		\
	fliphmtgen.c		\
	fmorphauto.c		\
	fmorphgen.1.c		\
	fmorphgenlow.1.c	\
	fpix1.c			\
	fpix2.c			\
	gifio.c			\
	gifiostub.c		\
	gplot.c			\
	graphics.c		\
	graymorph.c		\
	graymorphlow.c		\
	grayquant.c		\
	grayquantlow.c		\
	heap.c			\
	jbclass.c		\
	jpegio.c		\
	jpegiostub.c		\
	kernel.c		\
	list.c			\
	maze.c			\
	morphapp.c		\
	morph.c			\
	morphdwa.c		\
	morphseq.c		\
	numabasic.c		\
	numafunc1.c		\
	numafunc2.c		\
	pageseg.c		\
	paintcmap.c		\
	parseprotos.c		\
	partition.c		\
	pix1.c			\
	pix2.c			\
	pix3.c			\
	pix4.c			\
	pixabasic.c		\
	pixacc.c		\
	pixafunc1.c		\
	pixafunc2.c		\
	pixalloc.c		\
	pixarith.c		\
	pixconv.c		\
	pixtiling.c		\
	pngio.c			\
	pngiostub.c		\
	pnmio.c			\
	pnmiostub.c		\
	projective.c		\
	psio.c			\
	psiostub.c		\
	ptra.c			\
	pts.c			\
	queue.c			\
	rank.c			\
	readbarcode.c		\
	readfile.c		\
	rop.c			\
	ropiplow.c		\
	roplow.c		\
	rotateam.c		\
	rotateamlow.c		\
	rotate.c		\
	rotateorth.c		\
	rotateorthlow.c		\
	rotateshear.c		\
	runlength.c		\
	sarray.c		\
	scale.c			\
	scalelow.c		\
	seedfill.c		\
	seedfilllow.c		\
	sel1.c			\
	sel2.c			\
	selgen.c		\
	shear.c			\
	skew.c			\
	stack.c			\
	textops.c		\
	tiffio.c		\
	tiffiostub.c		\
	utils.c			\
	viewfiles.c		\
	warper.c		\
	watershed.c		\
	writefile.c		\
	zlibmem.c		\
	zlibmemstub.c


ifneq ($(TARGET_SIMULATOR),true)
LOCAL_SRC_FILES+=		\
	_open_memstream.c	\
	_fopencookie.c		\
	_fmemopen.c
endif

LOCAL_C_INCLUDES+=		\
	$(LOCAL_PATH)		\
	external/jpeg		\
	external/libpng		\
	external/zlib

LOCAL_CFLAGS:=			\
        -O3

LOCAL_STATIC_LIBRARIES:=	\
	libpng

LOCAL_SHARED_LIBRARIES:=	\
	libjpeg			\
        libz

LOCAL_MODULE:= liblept

LOCAL_PRELINK_MODULE:= false

include $(BUILD_SHARED_LIBRARY)
