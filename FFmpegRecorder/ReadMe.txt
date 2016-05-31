camera的数据如何去取出来

AVOutputFormat *av_guess_format(const char *short_name,
                                const char *filename,
                                const char *mime_type);
// 返回一个已经注册的最合适的输出格式
// 引入#include "libavformat/avformat.h"
// 可以通过 const char *short_name 获取,如"mpeg"
// 也可以通过 const char *filename 获取,如"E:\a.mp4"
