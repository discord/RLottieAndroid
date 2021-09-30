# This file is based on
# https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/jni/Android.mk
# but this file has stripped out the unneeded native libs and renamed the output library

MY_LOCAL_PATH := $(call my-dir)
LOCAL_PATH := $(MY_LOCAL_PATH)

LOCAL_CPPFLAGS := -frtti
LOCAL_CFLAGS += -DVERSION="1.3.1" -DFLAC__NO_MD5 -DFLAC__INTEGER_ONLY_LIBRARY -DFLAC__NO_ASM
LOCAL_CFLAGS += -D_REENTRANT -DPIC -DU_COMMON_IMPLEMENTATION -fPIC -DHAVE_SYS_PARAM_H
LOCAL_CFLAGS += -O3 -funroll-loops -finline-functions
LOCAL_ARM_MODE := arm
LOCAL_CPP_EXTENSION := .cc
LOCAL_MODULE := flac

include $(BUILD_STATIC_LIBRARY)

include $(CLEAR_VARS)

LOCAL_ARM_MODE  := arm
LOCAL_MODULE := lz4
LOCAL_CFLAGS 	:= -w -std=c11 -O3

LOCAL_SRC_FILES     := \
./lz4/lz4.c \
./lz4/lz4frame.c \
./lz4/xxhash.c

include $(BUILD_STATIC_LIBRARY)

include $(CLEAR_VARS)

LOCAL_ARM_MODE  := arm
LOCAL_MODULE := rlottie
LOCAL_CPPFLAGS := -DNDEBUG -Wall -std=c++14 -DANDROID -fno-rtti -DHAVE_PTHREAD -finline-functions -ffast-math -Os -fno-exceptions -fno-unwind-tables -fno-asynchronous-unwind-tables -Wnon-virtual-dtor -Woverloaded-virtual -Wno-unused-parameter -fvisibility=hidden
ifeq ($(TARGET_ARCH_ABI),$(filter $(TARGET_ARCH_ABI),armeabi-v7a))
 LOCAL_CFLAGS := -DUSE_ARM_NEON  -fno-integrated-as
 LOCAL_CPPFLAGS += -DUSE_ARM_NEON  -fno-integrated-as
else ifeq ($(TARGET_ARCH_ABI),$(filter $(TARGET_ARCH_ABI),arm64-v8a))
 LOCAL_CFLAGS := -DUSE_ARM_NEON -D__ARM64_NEON__ -fno-integrated-as
 LOCAL_CPPFLAGS += -DUSE_ARM_NEON -D__ARM64_NEON__ -fno-integrated-as
endif

LOCAL_C_INCLUDES := \
./jni/rlottie/inc \
./jni/rlottie/src/vector/ \
./jni/rlottie/src/vector/pixman \
./jni/rlottie/src/vector/freetype \
./jni/rlottie/src/vector/stb

LOCAL_SRC_FILES     := \
./rlottie/src/lottie/lottieanimation.cpp \
./rlottie/src/lottie/lottieitem.cpp \
./rlottie/src/lottie/lottiekeypath.cpp \
./rlottie/src/lottie/lottieloader.cpp \
./rlottie/src/lottie/lottiemodel.cpp \
./rlottie/src/lottie/lottieparser.cpp \
./rlottie/src/lottie/lottieproxymodel.cpp \
./rlottie/src/vector/freetype/v_ft_math.cpp \
./rlottie/src/vector/freetype/v_ft_raster.cpp \
./rlottie/src/vector/freetype/v_ft_stroker.cpp \
./rlottie/src/vector/pixman/vregion.cpp \
./rlottie/src/vector/stb/stb_image.cpp \
./rlottie/src/vector/vbezier.cpp \
./rlottie/src/vector/vbitmap.cpp \
./rlottie/src/vector/vbrush.cpp \
./rlottie/src/vector/vcompositionfunctions.cpp \
./rlottie/src/vector/vdasher.cpp \
./rlottie/src/vector/vdebug.cpp \
./rlottie/src/vector/vdrawable.cpp \
./rlottie/src/vector/vdrawhelper.cpp \
./rlottie/src/vector/vdrawhelper_neon.cpp \
./rlottie/src/vector/velapsedtimer.cpp \
./rlottie/src/vector/vimageloader.cpp \
./rlottie/src/vector/vinterpolator.cpp \
./rlottie/src/vector/vmatrix.cpp \
./rlottie/src/vector/vpainter.cpp \
./rlottie/src/vector/vpath.cpp \
./rlottie/src/vector/vpathmesure.cpp \
./rlottie/src/vector/vraster.cpp \
./rlottie/src/vector/vrect.cpp \
./rlottie/src/vector/vrle.cpp

ifeq ($(TARGET_ARCH_ABI),$(filter $(TARGET_ARCH_ABI),armeabi-v7a))
    LOCAL_SRC_FILES += ./rlottie/src/vector/pixman/pixman-arm-neon-asm.S.neon
else ifeq ($(TARGET_ARCH_ABI),$(filter $(TARGET_ARCH_ABI),arm64-v8a))
    LOCAL_SRC_FILES += ./rlottie/src/vector/pixman/pixman-arma64-neon-asm.S.neon
endif

LOCAL_STATIC_LIBRARIES := cpufeatures
include $(BUILD_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_PRELINK_MODULE := false

LOCAL_MODULE 	:= dsti # This determines the name of the exported library
LOCAL_CFLAGS 	:= -w -std=c11 -Os -DNULL=0 -DSOCKLEN_T=socklen_t -DLOCALE_NOT_USED -D_LARGEFILE_SOURCE=1
LOCAL_CFLAGS 	+= -Drestrict='' -D__EMX__ -DOPUS_BUILD -DFIXED_POINT -DUSE_ALLOCA -DHAVE_LRINT -DHAVE_LRINTF -fno-math-errno
LOCAL_CFLAGS 	+= -DANDROID_NDK -DDISABLE_IMPORTGL -fno-strict-aliasing -fprefetch-loop-arrays -DAVOID_TABLES -DANDROID_TILE_BASED_DECODE -DANDROID_ARMV6_IDCT -ffast-math -D__STDC_CONSTANT_MACROS
LOCAL_CPPFLAGS 	:= -DBSD=1 -ffast-math -Os -funroll-loops -std=c++14 -DPACKAGE_NAME='"drinkless/org/ton"'
LOCAL_LDLIBS 	:= -ljnigraphics -llog -lz -lEGL -lGLESv2 -landroid
LOCAL_STATIC_LIBRARIES := lz4 rlottie

LOCAL_C_INCLUDES    := \
./jni/rlottie/inc \
./jni/lz4


LOCAL_SRC_FILES += \
./lottie.cpp

include $(BUILD_SHARED_LIBRARY)

$(call import-module,android/cpufeatures)