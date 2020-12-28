LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
        $(call all-logtags-files-under, src)

LOCAL_MODULE := settings-logtags

include $(BUILD_STATIC_JAVA_LIBRARY)

# Build the Settings APK
include $(CLEAR_VARS)

LOCAL_PACKAGE_NAME := Settings
LOCAL_CERTIFICATE := platform
LOCAL_PRIVILEGED_MODULE := true
LOCAL_MODULE_TAGS := optional
LOCAL_USE_AAPT2 := true


src_dirs := src
res_dirs := res

ifneq ($(strip $(wildcard $(LOCAL_PATH)/../../../vendor/sprd/platform/packages/apps/AudioProfile)),)

audioprofile_dir := ./../../../vendor/sprd/platform/packages/apps/AudioProfile

src_dirs := src $(audioprofile_dir)/src

res_dirs := res $(audioprofile_dir)/res

LOCAL_AAPT_FLAGS := \
   --auto-add-overlay \
   --extra-packages com.sprd.audioprofile \
   --extra-packages android.support.design \
   --extra-packages android.support.v7.appcompat
endif

LOCAL_SRC_FILES := $(call all-java-files-under, $(src_dirs))

LOCAL_RESOURCE_DIR := $(addprefix $(LOCAL_PATH)/, $(res_dirs)) \
    frameworks/support/design/res \
    frameworks/support/v7/appcompat/res

LOCAL_STATIC_ANDROID_LIBRARIES := \
    android-support-v4 \
    android-support-v13 \
    android-support-v7-appcompat \
    android-support-v7-cardview \
    android-support-v7-preference \
    android-support-v7-recyclerview \
    android-support-v14-preference

LOCAL_JAVA_LIBRARIES := \
    bouncycastle \
    core-oj \
    telephony-common \
    ims-common \
    radio_interactor_common

LOCAL_STATIC_JAVA_LIBRARIES := \
    jsr305 \
    settings-logtags \
    android-support-design \
    android-support-v7-appcompat

LOCAL_PROGUARD_FLAG_FILES := proguard.flags

ifneq ($(INCREMENTAL_BUILDS),)
    LOCAL_PROGUARD_ENABLED := disabled
    LOCAL_JACK_ENABLED := incremental
    LOCAL_JACK_FLAGS := --multi-dex native
endif

include frameworks/opt/setupwizard/library/common-gingerbread.mk
include frameworks/base/packages/SettingsLib/common.mk
include $(BUILD_PACKAGE)

# Use the following include to make our test apk.
ifeq (,$(ONE_SHOT_MAKEFILE))
include $(call all-makefiles-under,$(LOCAL_PATH))
endif
