#include <stdint.h>
#include <string.h>

typedef struct {
    unsigned char *data;
    long bytes;
    long b_o_s;
    long e_o_s;
    int64_t granulepos;
    int64_t packetno;
} ogg_packet;

typedef struct {
    int   y_width;
    int   y_height;
    int   y_stride;
    int   uv_width;
    int   uv_height;
    int   uv_stride;
    unsigned char *y;
    unsigned char *u;
    unsigned char *v;
} yuv_buffer;

typedef struct {
    uint32_t width;
    uint32_t height;
    uint32_t frame_width;
    uint32_t frame_height;
    uint32_t offset_x;
    uint32_t offset_y;
    uint32_t fps_numerator;
    uint32_t fps_denominator;
    uint32_t aspect_numerator;
    uint32_t aspect_denominator;
    int      colorspace;
    int      target_bitrate;
    int      quality;
    int      quick_p;
    unsigned char version_major;
    unsigned char version_minor;
    unsigned char version_subminor;
    void *codec_setup;
    int      dropframes_p;
    int      keyframe_auto_p;
    uint32_t keyframe_frequency;
    uint32_t keyframe_frequency_force;
    uint32_t keyframe_data_target_bitrate;
    int32_t  keyframe_auto_threshold;
    uint32_t keyframe_mindistance;
    int32_t  noise_sensitivity;
    int32_t  sharpness;
    int      pixelformat;
} theora_info;

typedef struct {
    char **user_comments;
    int   *comment_lengths;
    int    comments;
    char  *vendor;
} theora_comment;

typedef struct {
    theora_info *i;
    int64_t granulepos;
    void *internal_decode;
} theora_state;

void theora_info_init(theora_info *c) {
    if (c) memset(c, 0, sizeof(*c));
}

void theora_info_clear(theora_info *c) {
    if (c) memset(c, 0, sizeof(*c));
}

void theora_comment_init(theora_comment *tc) {
    if (tc) memset(tc, 0, sizeof(*tc));
}

void theora_comment_clear(theora_comment *tc) {
    if (tc) memset(tc, 0, sizeof(*tc));
}

void theora_clear(theora_state *t) {
    if (t) memset(t, 0, sizeof(*t));
}

int theora_decode_header(theora_info *ci, theora_comment *cc, ogg_packet *op) {
    (void)ci; (void)cc; (void)op;
    return -1;
}

int theora_decode_init(theora_state *th, theora_info *c) {
    (void)th; (void)c;
    return -1;
}

int theora_packet_isheader(ogg_packet *op) {
    (void)op;
    return 0;
}

int theora_packet_iskeyframe(ogg_packet *op) {
    (void)op;
    return 0;
}

int theora_decode_packetin(theora_state *th, ogg_packet *op) {
    (void)th; (void)op;
    return -1;
}

int theora_decode_YUVout(theora_state *th, yuv_buffer *yuv) {
    (void)th; (void)yuv;
    return -1;
}

int64_t theora_granule_frame(theora_state *th, int64_t gp) {
    (void)th; (void)gp;
    return 0;
}

double theora_granule_time(theora_state *th, int64_t gp) {
    (void)th; (void)gp;
    return 0.0;
}
