/*
 * Copyright (C) 2009,2010,2011,2012,2013 Samuel Audet
 *
 * This file is part of JavaCV.
 *
 * JavaCV is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version (subject to the "Classpath" exception
 * as provided in the LICENSE.txt file that accompanied this code).
 *
 * JavaCV is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with JavaCV.  If not, see <http://www.gnu.org/licenses/>.
 *
 *
 * Based on the output-example.c file included in FFmpeg 0.6.5
 * as well as on the decoding_encoding.c file included in FFmpeg 0.11.1,
 * which are covered by the following copyright notice:
 *
 * Libavformat API example: Output a media file in any supported
 * libavformat format. The default codecs are used.
 *
 * Copyright (c) 2001,2003 Fabrice Bellard
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.qd.recorder;

import android.util.Log;

import com.googlecode.javacpp.BytePointer;
import com.googlecode.javacpp.DoublePointer;
import com.googlecode.javacpp.FloatPointer;
import com.googlecode.javacpp.IntPointer;
import com.googlecode.javacpp.Loader;
import com.googlecode.javacpp.Pointer;
import com.googlecode.javacpp.PointerPointer;
import com.googlecode.javacpp.ShortPointer;
import com.googlecode.javacv.FrameRecorder;
import com.googlecode.javacv.cpp.opencv_core;
import com.googlecode.javacv.cpp.opencv_imgproc;
import com.jack.util.JLog;

import java.io.File;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.Map.Entry;

import static com.googlecode.javacv.cpp.avcodec.*;
import static com.googlecode.javacv.cpp.opencv_imgproc.*;
import static com.googlecode.javacv.cpp.avformat.*;
import static com.googlecode.javacv.cpp.avutil.*;
import static com.googlecode.javacv.cpp.opencv_core.*;
import static com.googlecode.javacv.cpp.swresample.*;
import static com.googlecode.javacv.cpp.swscale.*;

/**
 *
 * @author Samuel Audet
 */
public class NewFFmpegFrameRecorder extends FrameRecorder {
    public static NewFFmpegFrameRecorder createDefault(File f, int w, int h)   throws Exception { return new NewFFmpegFrameRecorder(f, w, h); }
    public static NewFFmpegFrameRecorder createDefault(String f, int w, int h) throws Exception { return new NewFFmpegFrameRecorder(f, w, h); }

    private static Exception loadingException = null;
    public static void tryLoad() throws Exception {
        if (loadingException != null) {
            throw loadingException;
        } else {
            try {
                Loader.load(com.googlecode.javacv.cpp.avutil.class);
                Loader.load(com.googlecode.javacv.cpp.avcodec.class);
                Loader.load(com.googlecode.javacv.cpp.avformat.class);
                Loader.load(com.googlecode.javacv.cpp.swscale.class);
            } catch (Throwable t) {
                if (t instanceof Exception) {
                    throw loadingException = (Exception)t;
                } else {
                    throw loadingException = new Exception("Failed to load " + NewFFmpegFrameRecorder.class, t);
                }
            }
        }
    }

    static {
        /* initialize libavcodec, and register all codecs and formats */
        av_register_all();
        avformat_network_init();
    }

    public NewFFmpegFrameRecorder(File file, int audioChannels) {
        this(file, 0, 0, audioChannels);
    }
    public NewFFmpegFrameRecorder(String filename, int audioChannels) {
        this(filename, 0, 0, audioChannels);
    }
    public NewFFmpegFrameRecorder(File file, int imageWidth, int imageHeight) {
        this(file, imageWidth, imageHeight, 0);
    }
    public NewFFmpegFrameRecorder(String filename, int imageWidth, int imageHeight) {
        this(filename, imageWidth, imageHeight, 0);
    }
    public NewFFmpegFrameRecorder(File file, int imageWidth, int imageHeight, int audioChannels) {
        this(file.getAbsolutePath(), imageWidth, imageHeight);
    }
    public NewFFmpegFrameRecorder(String filename, int imageWidth, int imageHeight, int audioChannels) {
        this.filename      = filename;							//视频文件
        this.imageWidth    = imageWidth;
        this.imageHeight   = imageHeight;
        this.audioChannels = audioChannels;

        this.pixelFormat   = AV_PIX_FMT_NONE;
        this.videoCodec    = AV_CODEC_ID_NONE;
        this.videoBitrate  = 400000;
        this.frameRate     = 30;

        this.sampleFormat  = AV_SAMPLE_FMT_NONE;
        this.audioCodec    = AV_CODEC_ID_NONE;
        this.audioBitrate  = 64000;
        this.sampleRate    = 44100;

        this.interleaved = true;

        this.video_pkt = new AVPacket();						//视频Packet和音频Packet
        this.audio_pkt = new AVPacket();
    }
    public void release() throws Exception {
        synchronized (com.googlecode.javacv.cpp.avcodec.class) {
            releaseUnsafe();
        }
    }
    public void releaseUnsafe() throws Exception {
        /* close each codec */
        if (video_c != null) {
            avcodec_close(video_c);
            video_c = null;
        }
        if (audio_c != null) {
            avcodec_close(audio_c);
            audio_c = null;
        }
        if (picture_buf != null) {
            av_free(picture_buf);
            picture_buf = null;
        }
        if (picture != null) {
            avcodec_free_frame(picture);
            picture = null;
        }
        if (tmp_picture != null) {
            avcodec_free_frame(tmp_picture);
            tmp_picture = null;
        }
        if (video_outbuf != null) {
            av_free(video_outbuf);
            video_outbuf = null;
        }
        if (frame != null) {
            avcodec_free_frame(frame);
            frame = null;
        }
        if (samples_out != null) {
            for (int i = 0; i < samples_out.length; i++) {
                av_free(samples_out[i].position(0));
            }
            samples_out = null;
        }
        if (audio_outbuf != null) {
            av_free(audio_outbuf);
            audio_outbuf = null;
        }
        video_st = null;
        audio_st = null;

        if (oc != null && !oc.isNull()) {
            if ((oformat.flags() & AVFMT_NOFILE) == 0) {
                /* close the output file */
                avio_close(oc.pb());
            }

            /* free the streams */
            int nb_streams = oc.nb_streams();
            for(int i = 0; i < nb_streams; i++) {
                av_free(oc.streams(i).codec());
                av_free(oc.streams(i));
            }

            /* free the stream */
            av_free(oc);
            oc = null;
        }

        if (img_convert_ctx != null) {
            sws_freeContext(img_convert_ctx);
            img_convert_ctx = null;
        }

        if (samples_convert_ctx != null) {
            swr_free(samples_convert_ctx);
            samples_convert_ctx = null;
        }
    }
    @Override protected void finalize() throws Throwable {
        super.finalize();
        release();
    }

    private String filename;
    private AVFrame picture, tmp_picture;
    private BytePointer picture_buf;
    private BytePointer video_outbuf;
    private int video_outbuf_size;
    private AVFrame frame;
    private Pointer[] samples_in;
    private BytePointer[] samples_out;
    private PointerPointer samples_in_ptr;
    private PointerPointer samples_out_ptr;
    private BytePointer audio_outbuf;
    private int audio_outbuf_size;
    private int audio_input_frame_size;
    private AVOutputFormat oformat;
    private AVFormatContext oc;
    private AVCodec video_codec, audio_codec;
    private AVCodecContext video_c, audio_c;
    private AVStream video_st, audio_st;
    private SwsContext img_convert_ctx;
    private SwrContext samples_convert_ctx;
    private AVPacket video_pkt, audio_pkt;
    private int[] got_video_packet, got_audio_packet;

    @Override public int getFrameNumber() {
        return picture == null ? super.getFrameNumber() : (int)picture.pts();
    }
    @Override public void setFrameNumber(int frameNumber) {
        if (picture == null) { super.setFrameNumber(frameNumber); } else { picture.pts(frameNumber); }
    }

    // best guess for timestamp in microseconds...
    @Override public long getTimestamp() {
        return Math.round(getFrameNumber() * 1000000L / getFrameRate());
    }
    @Override public void setTimestamp(long timestamp)  {
        setFrameNumber((int)Math.round(timestamp * getFrameRate() / 1000000L));
    }

    public void start() throws Exception {
        synchronized (com.googlecode.javacv.cpp.avcodec.class) {
            startUnsafe();
        }
    }
    public void startUnsafe() throws Exception {
        int ret;
        picture = null;
        tmp_picture = null;
        picture_buf = null;
        frame = null;
        video_outbuf = null;
        audio_outbuf = null;
        oc = null;
        video_c = null;
        audio_c = null;
        video_st = null;
        audio_st = null;
        got_video_packet = new int[1];
        got_audio_packet = new int[1];
        JLog.print("文件名："+filename);
        /* auto detect the output format from the name. */ 
        String format_name = format == null || format.length() == 0 ? null : format; 						//format是其父类的一个成员  前面setFormat设置为mp4
        JLog.print("format name: " + format_name);
        if ((oformat = av_guess_format(format_name, filename, null)) == null) {									//根据文件名，生成一个合适的输出AVOutputFormat的结构体
        	
        	int proto = filename.indexOf("://");
            if (proto > 0) {
                format_name = filename.substring(0, proto);
            }
            if ((oformat = av_guess_format(format_name, filename, null)) == null) {
                throw new Exception("av_guess_format() error: Could not guess output format for \"" + filename + "\" and " + format + " format.");
            }
        }
        format_name = oformat.name().getString();
        JLog.print("format_name："+format_name);
        /* allocate the output media context */
        if ((oc = avformat_alloc_context()) == null) {								//分配一个AVFormatContext结构体
            JLog.print("分配AVFormatContext结构体失败，oc");
        	throw new Exception("avformat_alloc_context() error: Could not allocate format context");
            
        }
        JLog.print("分配AVFormatContext结构体成功oc");
        oc.oformat(oformat);					//去设置AVFormatContext里面的一个变了struct AVOutputFormat *oformat;
        oc.filename().putString(filename);		//设置其名称
        JLog.print("给AVFormatContext分配的文字为: " + filename);

        /* add the audio and video streams using the format codecs
           and initialize the codecs */

        if (imageWidth > 0 && imageHeight > 0) {						//前面一个步骤写死了，480*480 ，通过构造方法传入的
            if (videoCodec != AV_CODEC_ID_NONE) {					//videoCodec视频解码器id，该指在Util里面写死了为AV_CODEC_MPEG4
                oformat.video_codec(videoCodec);						//设置输出videocodec编码器id
            } else if ("flv".equals(format_name)) {
                oformat.video_codec(AV_CODEC_ID_FLV1);
            } else if ("mp4".equals(format_name)) {
                oformat.video_codec(AV_CODEC_ID_MPEG4);
            } else if ("3gp".equals(format_name)) {
                oformat.video_codec(AV_CODEC_ID_H263);
            } else if ("avi".equals(format_name)) {
                oformat.video_codec(AV_CODEC_ID_HUFFYUV);
            }
            JLog.print("videoCodecName  is " + videoCodecName);				//尼玛  这个videoCodecName竟然是个空
            /* find the video encoder   寻找视频编码器*/ 
            if ((video_codec = avcodec_find_encoder_by_name(videoCodecName)) == null &&
                (video_codec = avcodec_find_encoder(oformat.video_codec())) == null) {
                release();
                JLog.print("avcod	ec_find_ecodec error" );
                throw new Exception("avcodec_find_encoder() error: Video codec not found.");
            }
            JLog.print("成功找到视频编码器");
            //转换时基
            AVRational frame_rate = av_d2q(frameRate, 1001000);									//frameRate 在构造方法里面写死了 30 
            AVRational supported_framerates = video_codec.supported_framerates();		//获取编码器AVCodec里面支持的帧频率   supported_framerates是AVCodec结构体里面的一个变量
            if (supported_framerates != null) {
                int idx = av_find_nearest_q_idx(frame_rate, supported_framerates);					//av_find_nearest_q_idx()在avutil模块rational.h头文件里面   该方法是从参数2数组里面寻找一个和参数1最为接近的数，并返回序列id
                frame_rate = supported_framerates.position(idx);
            }

            /* add a video output stream 
             * avformat_new_stream()  --- avformat.h
             * 往media file里面增加一条AVstream流
             * oc - AVFormatContext的媒体文件句柄，  video_codec指定这条流的编码器
             * */
            if ((video_st = avformat_new_stream(oc, video_codec)) == null) {
                release();
                JLog.print("创键并往媒体文件添加流失败");
                throw new Exception("avformat_new_stream() error: Could not allocate video stream.");
            }
            JLog.print("创建流成功 -- avformat_new_stream");
            video_c = video_st.codec();																													//获取AVStream -- video_st里面的上下文AVCodecContext
            video_c.codec_id(oformat.video_codec());																					//设置编码器id  AVCodecID， 把输出流的video_codec设置给新增加的流
            video_c.codec_type(AVMEDIA_TYPE_VIDEO);																				//设置该流为衣蛾视频流  AVMEDIA_TYPE_VIDEO在avutil.h里面

            /* put sample parameters */
            video_c.bit_rate(videoBitrate);						 																					//设置AVCodecContext的bit_rate比特率 video bit rate写死了1000000
            /* resolution must be a multiple of two, but round up to 16 as often required */
            video_c.width((imageWidth + 15) / 16 * 16);																				//480 × 480
            video_c.height(imageHeight);																											//设置其结构体的width, height  视频页面
            /* time base: this is the fundamental unit of time (in seconds) in terms
               of which frame timestamps are represented. for fixed-fps content,
               timebase should be 1/framerate and timestamp increments should be
               identically 1. */
            video_c.time_base(av_inv_q(frame_rate));
            video_c.gop_size(12); /* emit one intra frame every twelve frames at most */
            if (videoQuality >= 0) {									//videoQuality写死了 12
                video_c.flags(video_c.flags() | CODEC_FLAG_QSCALE);														//在avcodec里面
                video_c.global_quality((int)Math.round(FF_QP2LAMBDA * videoQuality));			//
            }
            
            //根据编码器上下文的编码器id确定输出的媒体流格式 pix format
            if (pixelFormat != AV_PIX_FMT_NONE) {
            	JLog.print("pixformat is None");
                video_c.pix_fmt(pixelFormat);
            } else if (video_c.codec_id() == AV_CODEC_ID_RAWVIDEO || video_c.codec_id() == AV_CODEC_ID_PNG ||
                       video_c.codec_id() == AV_CODEC_ID_HUFFYUV  || video_c.codec_id() == AV_CODEC_ID_FFV1) {
            	JLog.print("pixformat is AV_PIX_FMT_RGB32");
                video_c.pix_fmt(AV_PIX_FMT_RGB32);   // appropriate for common lossless formats
            } else {
            	JLog.print("pixformat is AV_PIX_FMT_YUV420P");
                video_c.pix_fmt(AV_PIX_FMT_YUV420P); // lossy, but works with about everything
            }

            if (video_c.codec_id() == AV_CODEC_ID_MPEG2VIDEO) {
                /* just for testing, we also add B frames */
            	JLog.print("AVCodecContext  is AV_CODEC_ID_MPEG2VIDEO");
                video_c.max_b_frames(2);																						//非B帧之间的B帧最大数量  B帧是双向帧
            } else if (video_c.codec_id() == AV_CODEC_ID_MPEG1VIDEO) {
                /* Needed to avoid using macroblocks in which some coeffs overflow.
                   This does not happen with normal video, it just happens here as
                   the motion of the chroma plane does not match the luma plane. */
            	JLog.print("AVCodecContext  is AV_CODEC_ID_MPEG1VIDEO");
                video_c.mb_decision(2);
            } else if (video_c.codec_id() == AV_CODEC_ID_H263) {
            	JLog.print("AVCodecContext  is AV_CODEC_ID_H263");
                // H.263 does not support any other resolution than the following
                if (imageWidth <= 128 && imageHeight <= 96) {
                    video_c.width(128).height(96);
                } else if (imageWidth <= 176 && imageHeight <= 144) {
                    video_c.width(176).height(144);
                } else if (imageWidth <= 352 && imageHeight <= 288) {
                    video_c.width(352).height(288);
                } else if (imageWidth <= 704 && imageHeight <= 576) {
                    video_c.width(704).height(576);
                } else {
                    video_c.width(1408).height(1152);
                }
            } else if (video_c.codec_id() == AV_CODEC_ID_H264) {
            	JLog.print("AVCodecContext  is AV_CODEC_ID_H264");
                // default to constrained baseline to produce content that plays back on anything,
                // without any significant tradeoffs for most use cases
                video_c.profile(AVCodecContext.FF_PROFILE_H264_CONSTRAINED_BASELINE);
            }

            // some formats want stream headers to be separate
            if ((oformat.flags() & AVFMT_GLOBALHEADER) != 0) {
                video_c.flags(video_c.flags() | CODEC_FLAG_GLOBAL_HEADER);
            }

            if ((video_codec.capabilities() & CODEC_CAP_EXPERIMENTAL) != 0) {
                video_c.strict_std_compliance(AVCodecContext.FF_COMPLIANCE_EXPERIMENTAL);
            }
        }

        /*
         * add an audio output stream
         */
        JLog.print("audioChannels checking ..........................." + audioChannels);
        if (audioChannels > 0 && audioBitrate > 0 && sampleRate > 0) {								//audioChannels等几个参数都在构造方法中设置了1
            //设置音频编码器ID，  先看有没有初始化，如果没有则根据文件名来确定其格式
        	if (audioCodec != AV_CODEC_ID_NONE) {					
                oformat.audio_codec(audioCodec);
                //flv mp4 3gp ---- AV_CODEC_ID_AAC  而avi则是AV_CODEC_ID_PCM_S16LE
            } else if ("flv".equals(format_name) || "mp4".equals(format_name) || "3gp".equals(format_name)) {
                oformat.audio_codec(AV_CODEC_ID_AAC);
            } else if ("avi".equals(format_name)) {
                oformat.audio_codec(AV_CODEC_ID_PCM_S16LE);
            }

            /* find the audio encoder */
        	JLog.print("设置的音频解码器名称为： " + audioCodecName);
            if ((audio_codec = avcodec_find_encoder_by_name(audioCodecName)) == null &&
                (audio_codec = avcodec_find_encoder(oformat.audio_codec())) == null) {
                release();
                throw new Exception("avcodec_find_encoder() error: Audio codec not found.");
            }
            //往媒体文件oc里面增加一条音频流
            if ((audio_st = avformat_new_stream(oc, audio_codec)) == null) {
                release();
                throw new Exception("avformat_new_stream() error: Could not allocate audio stream.");
            }
            JLog.print("增加音频流成功");
            //audio_c是AVCodecContext上下文
            audio_c = audio_st.codec();
            audio_c.codec_id(oformat.audio_codec());							//设置音频编码器id
            audio_c.codec_type(AVMEDIA_TYPE_AUDIO);					//编解码器类型

            /* put sample parameters */
            audio_c.bit_rate(audioBitrate);
            audio_c.sample_rate(sampleRate);
            audio_c.channels(audioChannels);				//声道数  声道是什么东西，可以去百度一下   有单声道  双声道  四声道环绕等
            JLog.print("声道数 "+audioChannels);
            audio_c.channel_layout(av_get_default_channel_layout(audioChannels));
            if (sampleFormat != AV_SAMPLE_FMT_NONE) {														//设置AVCodecContext的采样格式   初始化时设置，例如AV_SAMPLE_FMT_NONE = -1, AV_SAMPLE_FMT_U8,          ///< unsigned 8 bits  
                audio_c.sample_fmt(sampleFormat);
            } else if (audio_c.codec_id() == AV_CODEC_ID_AAC &&
                    (audio_codec.capabilities() & CODEC_CAP_EXPERIMENTAL) != 0) {
                audio_c.sample_fmt(AV_SAMPLE_FMT_FLTP);
            } else {
                audio_c.sample_fmt(AV_SAMPLE_FMT_S16);
            }
            JLog.print("音频采样频率sampleRate: " + sampleRate);
            audio_c.time_base().num(1).den(sampleRate);									//根据采样频率换算时基AVRation
            switch (audio_c.sample_fmt()) {
                case AV_SAMPLE_FMT_U8:
                case AV_SAMPLE_FMT_U8P:  audio_c.bits_per_raw_sample(8);  break;
                case AV_SAMPLE_FMT_S16:
                case AV_SAMPLE_FMT_S16P: audio_c.bits_per_raw_sample(16); break;					//将转换格式sampleFormat量化到每秒采集多少位
                case AV_SAMPLE_FMT_S32:
                case AV_SAMPLE_FMT_S32P: audio_c.bits_per_raw_sample(32); break;
                case AV_SAMPLE_FMT_FLT:
                case AV_SAMPLE_FMT_FLTP: audio_c.bits_per_raw_sample(32); break;
                case AV_SAMPLE_FMT_DBL:
                case AV_SAMPLE_FMT_DBLP: audio_c.bits_per_raw_sample(64); break;
                default: assert false;
            }
            if (audioQuality >= 0) {
                audio_c.flags(audio_c.flags() | CODEC_FLAG_QSCALE);								//设置音频高质量
                audio_c.global_quality((int)Math.round(FF_QP2LAMBDA * audioQuality));
            }

            // some formats want stream headers to be separate 如果媒体流需要包头header，给音频流也添设置包头标志
            if ((oformat.flags() & AVFMT_GLOBALHEADER) != 0) {
                audio_c.flags(audio_c.flags() | CODEC_FLAG_GLOBAL_HEADER);
            }
            //硬件加速度计
            if ((audio_codec.capabilities() & CODEC_CAP_EXPERIMENTAL) != 0) {
                audio_c.strict_std_compliance(AVCodecContext.FF_COMPLIANCE_EXPERIMENTAL);
            }
        }

        JLog.print("filename is " + filename);
        av_dump_format(oc, 0, filename, 1);				//调试函数，打印输出媒体流信息  第一个是媒体上下文  第二个是哪条流  第三个输出的url  第四个是0input和1output

        /* now that all the parameters are set, we can open the audio and
           video codecs and allocate the necessary encode buffers */
        if (video_st != null) {
            AVDictionary options = new AVDictionary(null);						//AVFormatContext里面的一个成员  元数据 ;通常她的作用是作为一个附加值添加到某些命令当中去,
            if (videoQuality >= 0) {
                av_dict_set(options, "crf", "" + videoQuality, 0);					 //av_dict_set以键值对的形式往AVDictionary里面添加数据
            }
            for (Entry<String, String> e : videoOptions.entrySet()) {
                av_dict_set(options, e.getKey(), e.getValue(), 0);
            }
            JLog.print("打开视频编码器");
            //打开编码器
            /* open the codec */
            if ((ret = avcodec_open2(video_c, video_codec, options)) < 0) {
                release();
                throw new Exception("avcodec_open2() error " + ret + ": Could not open video codec.");
            }
            //释放附加参数
            av_dict_free(options);

            video_outbuf = null;
            if ((oformat.flags() & AVFMT_RAWPICTURE) == 0) {
                /* allocate output buffer */
                /* XXX: API change will be done */
                /* buffers passed into lav* can be allocated any way you prefer,
                   as long as they're aligned enough for the architecture, and
                   they're freed appropriately (such as using av_free for buffers
                   allocated with av_malloc) */
                video_outbuf_size = Math.max(256 * 1024, 8 * video_c.width() * video_c.height()); // a la ffmpeg.c
                video_outbuf = new BytePointer(av_malloc(video_outbuf_size));
            }

            /* allocate the encoded raw picture */
            if ((picture = avcodec_alloc_frame()) == null) {
                release();
                throw new Exception("avcodec_alloc_frame() error: Could not allocate picture.");
            }
            picture.pts(0); // magic required by libx264
            
            int size = avpicture_get_size(video_c.pix_fmt(), video_c.width(), video_c.height());				//获取一张图片所占用的内存
            JLog.print("图片大小和尺寸：total size " + size + "pix_fmt "+video_c.pix_fmt() + "video width " +  video_c.width() + "video height " + video_c.height() );
            if ((picture_buf = new BytePointer(av_malloc(size))).isNull()) {
                release();
                throw new Exception("av_malloc() error: Could not allocate picture buffer.");
            }

            /* if the output format is not equal to the image format, then a temporary
               picture is needed too. It is then converted to the required output format */
            if ((tmp_picture = avcodec_alloc_frame()) == null) {
                release();
                throw new Exception("avcodec_alloc_frame() error: Could not allocate temporary picture.");
            }
        }

        if (audio_st != null) {
            AVDictionary options = new AVDictionary(null);
            if (audioQuality >= 0) {
                av_dict_set(options, "crf", "" + audioQuality, 0);
            }
            for (Entry<String, String> e : audioOptions.entrySet()) {
                av_dict_set(options, e.getKey(), e.getValue(), 0);
            }
            /* open the codec */
            if ((ret = avcodec_open2(audio_c, audio_codec, options)) < 0) {
                release();
                throw new Exception("avcodec_open2() error " + ret + ": Could not open audio codec.");
            }
            av_dict_free(options);

            audio_outbuf_size = 256 * 1024;
            audio_outbuf = new BytePointer(av_malloc(audio_outbuf_size));

            /* 	ugly hack for PCM codecs (will be removed ASAP with new PCM
               support to compute the input frame size in samples */
            if (audio_c.frame_size() <= 1) {
                audio_outbuf_size = FF_MIN_BUFFER_SIZE;
                audio_input_frame_size = audio_outbuf_size / audio_c.channels();
                switch (audio_c.codec_id()) {
                    case AV_CODEC_ID_PCM_S16LE:
                    case AV_CODEC_ID_PCM_S16BE:
                    case AV_CODEC_ID_PCM_U16LE:
                    case AV_CODEC_ID_PCM_U16BE:
                        audio_input_frame_size >>= 1;
                        break;
                    default:
                        break;
                }
            } else {
                audio_input_frame_size = audio_c.frame_size();
            }
            
            //int bufferSize = audio_input_frame_size * audio_c.bits_per_raw_sample()/8 * audio_c.channels();
            int planes = av_sample_fmt_is_planar(audio_c.sample_fmt()) != 0 ? (int)audio_c.channels() : 1;						//av_sample_fmt_is_planar  监测音频样本是否需要分片
            int data_size = av_samples_get_buffer_size((IntPointer)null, audio_c.channels(),													//av_samples_get_buffer_size 采样的音频占用的i字节数 = 通道数 × 采样频率 × 采样位数
                    audio_input_frame_size, audio_c.sample_fmt(), 1) / planes;
            samples_out = new BytePointer[planes];
            for (int i = 0; i < samples_out.length; i++) {
                samples_out[i] = new BytePointer(av_malloc(data_size)).capacity(data_size);						//capacity 设置Pointer里面的capacity变量
            }
            samples_in = new Pointer[AVFrame.AV_NUM_DATA_POINTERS];												//数组
            samples_in_ptr  = new PointerPointer(AVFrame.AV_NUM_DATA_POINTERS);						//native层去分配数组
            samples_out_ptr = new PointerPointer(AVFrame.AV_NUM_DATA_POINTERS);

            /* allocate the audio frame  创建音频帧的数据结构AVframe*/   
            if ((frame = avcodec_alloc_frame()) == null) {
                release();
                throw new Exception("avcodec_alloc_frame() error: Could not allocate audio frame.");
            }
        }
        JLog.print("查看数据是不是从磁盘输出");
        /* open the output file, if needed */
        if ((oformat.flags() & AVFMT_NOFILE) == 0) {
        	JLog.print("不是从磁盘输出");
            AVIOContext pb = new AVIOContext(null);					//其中AVIOContext是FFMPEG管理输入输出数据的结构体
            if ((ret = avio_open(pb, filename, AVIO_FLAG_WRITE)) < 0) {					//avio_opne用于打开输入输出文件
                release();
                JLog.print("打开输出文件失败" + filename);
                throw new Exception("avio_open error() error " + ret + ": Could not open '" + filename + "'");
            }
            oc.pb(pb);						//设置AVFormatContext的AVIOContext
        }

        /* write the stream header, if any */
        avformat_write_header(oc, (PointerPointer)null);
    }

    public void stop() throws Exception {
        if (oc != null) {
            try {
                /* flush all the buffers */
                while (video_st != null && record((IplImage)null, AV_PIX_FMT_NONE));
                while (audio_st != null && record((AVFrame)null));

                if (interleaved && video_st != null && audio_st != null) {
                    av_interleaved_write_frame(oc, null);
                } else {
                    av_write_frame(oc, null);
                }

                /* write the trailer, if any */
                av_write_trailer(oc);
            } finally {
                release();
            }
        }
    }
    
 // 逆时针旋转图像degree角度（原尺寸）
 	private IplImage rotateImage(IplImage img)
     {
     	/*IplImage img_rotate = IplImage.create(img.width(), img.height(),  IPL_DEPTH_8U, 2);
     	//旋转中心为图像中心
     	CvPoint2D32f center = new CvPoint2D32f(); 
     	center.x(img.width()/2.0f+0.5f);
     	center.y(img.height()/2.0f+0.5f);
     	//计算二维旋转的仿射变换矩阵
     	CvMat cvMat = cvCreateMat(2, 3, CV_32F);
     	
     	cvZero (img_rotate);
     	cv2DRotationMatrix( center, degree,1.0, cvMat);*/
     	
     	//变换图像，并用黑色填充其余值
     	//cvWarpAffine(img,img_rotate, cvMat,CV_INTER_LINEAR+CV_WARP_FILL_OUTLIERS,cvScalarAll(0) );
 		IplImage img_rotate = IplImage.create(img.height(),img.width(),  IPL_DEPTH_8U, 2);
 		cvTranspose(img, img_rotate);
 		cvTranspose(img_rotate, img);
 		//cvTranspose(img, img_rotate);
     	cvFlip(img,null,-1);
     	
     	return img;
     }
 	
 	 public static IplImage rotate(IplImage image, double angle) {


         IplImage copy = cvCloneImage(image);
         IplImage rotatedImage = cvCreateImage(cvGetSize(copy), copy.depth(), copy.nChannels());

         //Define Rotational Matrix
         CvMat mapMatrix = cvCreateMat(2, 3, CV_32FC1);

         //Define Mid Point
         CvPoint2D32f centerPoint = new CvPoint2D32f();
         centerPoint.x(copy.width() / 2);
         centerPoint.y(copy.height() / 2);

         //Get Rotational Matrix
         cv2DRotationMatrix(centerPoint, angle, 1.0, mapMatrix);

         //Rotate the Image
         cvWarpAffine(copy, rotatedImage, mapMatrix, CV_INTER_CUBIC + CV_WARP_FILL_OUTLIERS, cvScalarAll(170));
         cvReleaseImage(copy);
         cvReleaseMat(mapMatrix);

         return rotatedImage;
     }
 	 

    public boolean record(IplImage image) throws Exception {
        return record(image, AV_PIX_FMT_NONE);
    }
    public boolean record(IplImage image, int pixelFormat) throws Exception {
        if (video_st == null) {
            throw new Exception("No video output stream (Is imageWidth > 0 && imageHeight > 0 and has start() been called?)");
        }
        int ret;

        if (image == null) {
            /* no more frame to compress. The codec has a latency of a few
               frames if using B frames, so we get the last frames by
               passing the same picture again */
        } else {
        	
        	//image = rotate(image,90);

            int width = image.width();
            int height = image.height();
            int step = image.widthStep();
            BytePointer data = image.imageData();

            if (pixelFormat == AV_PIX_FMT_NONE) {
                int depth = image.depth();
                int channels = image.nChannels();
                if ((depth == IPL_DEPTH_8U || depth == IPL_DEPTH_8S) && channels == 3) {
                    pixelFormat = AV_PIX_FMT_BGR24;
                } else if ((depth == IPL_DEPTH_8U || depth == IPL_DEPTH_8S) && channels == 1) {
                    pixelFormat = AV_PIX_FMT_GRAY8;
                } else if ((depth == IPL_DEPTH_16U || depth == IPL_DEPTH_16S) && channels == 1) {
                    pixelFormat = ByteOrder.nativeOrder().equals(ByteOrder.BIG_ENDIAN) ?
                            AV_PIX_FMT_GRAY16BE : AV_PIX_FMT_GRAY16LE;
                } else if ((depth == IPL_DEPTH_8U || depth == IPL_DEPTH_8S) && channels == 4) {
                    pixelFormat = AV_PIX_FMT_RGBA;
                } else if ((depth == IPL_DEPTH_8U || depth == IPL_DEPTH_8S) && channels == 2) {
                    pixelFormat = AV_PIX_FMT_NV21; // Android's camera capture format
                    step = width;
                } else {
                    throw new Exception("Could not guess pixel format of image: depth=" + depth + ", channels=" + channels);
                }
            }

            if (video_c.pix_fmt() != pixelFormat || video_c.width() != width || video_c.height() != height) {
                /* convert to the codec pixel format if needed */
            	img_convert_ctx = sws_getCachedContext(img_convert_ctx,
						video_c.width(), video_c.height(), pixelFormat,
						video_c.width(), video_c.height(), video_c.pix_fmt(),
                        SWS_BILINEAR,null, null, (DoublePointer)null);
                if (img_convert_ctx == null) {
                    throw new Exception("sws_getCachedContext() error: Cannot initialize the conversion context.");
                }
                avpicture_fill(new AVPicture(tmp_picture), data, pixelFormat, width, height);
                avpicture_fill(new AVPicture(picture), picture_buf, video_c.pix_fmt(), video_c.width(), video_c.height());
                tmp_picture.linesize(0, step);
                sws_scale(img_convert_ctx, new PointerPointer(tmp_picture), tmp_picture.linesize(),
                          0, height, new PointerPointer(picture), picture.linesize());
            } else {
                avpicture_fill(new AVPicture(picture), data, pixelFormat, width, height);
                picture.linesize(0, step);
            }
        }

        if ((oformat.flags() & AVFMT_RAWPICTURE) != 0) {
            if (image == null) {
                return false;
            }
            /* raw video case. The API may change slightly in the future for that? */
            av_init_packet(video_pkt);
            video_pkt.flags(video_pkt.flags() | AV_PKT_FLAG_KEY);
            video_pkt.stream_index(video_st.index());
            video_pkt.data(new BytePointer(picture));
            video_pkt.size(Loader.sizeof(AVPicture.class));
        } else {
            /* encode the image */
            av_init_packet(video_pkt);
            video_pkt.data(video_outbuf);
            video_pkt.size(video_outbuf_size);
            picture.quality(video_c.global_quality());
            if ((ret = avcodec_encode_video2(video_c, video_pkt, image == null ? null : picture, got_video_packet)) < 0) {
                throw new Exception("avcodec_encode_video2() error " + ret + ": Could not encode video packet.");
            }
            picture.pts(picture.pts() + 1); // magic required by libx264

            /* if zero size, it means the image was buffered */
            if (got_video_packet[0] != 0) {
                if (video_pkt.pts() != AV_NOPTS_VALUE) {
                    video_pkt.pts(av_rescale_q(video_pkt.pts(), video_c.time_base(), video_st.time_base()));
                }
                if (video_pkt.dts() != AV_NOPTS_VALUE) {
                    video_pkt.dts(av_rescale_q(video_pkt.dts(), video_c.time_base(), video_st.time_base()));
                }
                video_pkt.stream_index(video_st.index());
            } else {
                return false;
            }
        }

        synchronized (oc) {
            /* write the compressed frame in the media file */
            if (interleaved && audio_st != null) {
                if ((ret = av_interleaved_write_frame(oc, video_pkt)) < 0) {
                    throw new Exception("av_interleaved_write_frame() error " + ret + " while writing interleaved video frame.");
                }
            } else {
                if ((ret = av_write_frame(oc, video_pkt)) < 0) {
                    throw new Exception("av_write_frame() error " + ret + " while writing video frame.");
                }
            }
        }
        return picture.key_frame() != 0;
    }

    @Override public boolean record(int sampleRate, Buffer ... samples) throws Exception {
        if (audio_st == null) {
            throw new Exception("No audio output stream (Is audioChannels > 0 and has start() been called?)");
        }
        int ret;

        int inputSize = samples[0].limit() - samples[0].position();
        int inputFormat = AV_SAMPLE_FMT_NONE;
        int inputChannels = samples.length > 1 ? 1 : audioChannels;
        int inputDepth = 0;
        int outputFormat = audio_c.sample_fmt();
        int outputChannels = samples_out.length > 1 ? 1 : audioChannels;
        int outputDepth = av_get_bytes_per_sample(outputFormat);
        if (sampleRate <= 0) {
            sampleRate = audio_c.sample_rate();
        }
        if (samples[0] instanceof ByteBuffer) {
            inputFormat = samples.length > 1 ? AV_SAMPLE_FMT_U8P : AV_SAMPLE_FMT_U8;
            inputDepth = 1;
            for (int i = 0; i < samples.length; i++) {
                ByteBuffer b = (ByteBuffer)samples[i];
                if (samples_in[i] instanceof BytePointer && samples_in[i].capacity() >= inputSize && b.hasArray()) {
                    ((BytePointer)samples_in[i]).position(0).put(b.array(), b.position(), inputSize);
                } else {
                    samples_in[i] = new BytePointer(b);
                }
            }
        } else if (samples[0] instanceof ShortBuffer) {
            inputFormat = samples.length > 1 ? AV_SAMPLE_FMT_S16P : AV_SAMPLE_FMT_S16;
            inputDepth = 2;
            for (int i = 0; i < samples.length; i++) {
                ShortBuffer b = (ShortBuffer)samples[i];
                if (samples_in[i] instanceof ShortPointer && samples_in[i].capacity() >= inputSize && b.hasArray()) {
                    ((ShortPointer)samples_in[i]).position(0).put(b.array(), samples[i].position(), inputSize);
                } else {
                    samples_in[i] = new ShortPointer(b);
                }
            }
        } else if (samples[0] instanceof IntBuffer) {
            inputFormat = samples.length > 1 ? AV_SAMPLE_FMT_S32P : AV_SAMPLE_FMT_S32;
            inputDepth = 4;
            for (int i = 0; i < samples.length; i++) {
                IntBuffer b = (IntBuffer)samples[i];
                if (samples_in[i] instanceof IntPointer && samples_in[i].capacity() >= inputSize && b.hasArray()) {
                    ((IntPointer)samples_in[i]).position(0).put(b.array(), samples[i].position(), inputSize);
                } else {
                    samples_in[i] = new IntPointer(b);
                }
            }
        } else if (samples[0] instanceof FloatBuffer) {
            inputFormat = samples.length > 1 ? AV_SAMPLE_FMT_FLTP : AV_SAMPLE_FMT_FLT;
            inputDepth = 4;
            for (int i = 0; i < samples.length; i++) {
                FloatBuffer b = (FloatBuffer)samples[i];
                if (samples_in[i] instanceof FloatPointer && samples_in[i].capacity() >= inputSize && b.hasArray()) {
                    ((FloatPointer)samples_in[i]).position(0).put(b.array(), b.position(), inputSize);
                } else {
                    samples_in[i] = new FloatPointer(b);
                }
            }
        } else if (samples[0] instanceof DoubleBuffer) {
            inputFormat = samples.length > 1 ? AV_SAMPLE_FMT_DBLP : AV_SAMPLE_FMT_DBL;
            inputDepth = 8;
            for (int i = 0; i < samples.length; i++) {
                DoubleBuffer b = (DoubleBuffer)samples[i];
                if (samples_in[i] instanceof DoublePointer && samples_in[i].capacity() >= inputSize && b.hasArray()) {
                    ((DoublePointer)samples_in[i]).position(0).put(b.array(), b.position(), inputSize);
                } else {
                    samples_in[i] = new DoublePointer(b);
                }
            }
        } else {
            throw new Exception("Audio samples Buffer has unsupported type: " + samples);
        }

        if (samples_convert_ctx == null) {
            samples_convert_ctx = swr_alloc_set_opts(null,
                    audio_c.channel_layout(), outputFormat, audio_c.sample_rate(),
                    audio_c.channel_layout(), inputFormat, sampleRate, 0, null);
            if (samples_convert_ctx == null) {
                throw new Exception("swr_alloc_set_opts() error: Cannot allocate the conversion context.");
            } else if ((ret = swr_init(samples_convert_ctx)) < 0) {
                throw new Exception("swr_init() error " + ret + ": Cannot initialize the conversion context.");
            }
        }

        for (int i = 0; i < samples.length; i++) {
            samples_in[i].position(samples_in[i].position() * inputDepth).
                    limit((samples_in[i].position() + inputSize) * inputDepth);
        }
        while (true) {
            int inputCount = (samples_in[0].limit() - samples_in[0].position()) / (inputChannels * inputDepth);
            int outputCount = (samples_out[0].limit() - samples_out[0].position()) / (outputChannels * outputDepth);
            inputCount = Math.min(inputCount, 2 * (outputCount * sampleRate) / audio_c.sample_rate());
            for (int i = 0; i < samples.length; i++) {
                samples_in_ptr.put(i, samples_in[i]);
            }
            for (int i = 0; i < samples_out.length; i++) {
                samples_out_ptr.put(i, samples_out[i]);
            }
            if ((ret = swr_convert(samples_convert_ctx, samples_out_ptr, outputCount, samples_in_ptr, inputCount)) < 0) {
                throw new Exception("swr_convert() error " + ret + ": Cannot convert audio samples.");
            } else if (ret == 0) {
                break;
            }
            for (int i = 0; i < samples.length; i++) {
                samples_in[i].position(samples_in[i].position() + inputCount * inputChannels * inputDepth);
            }
            for (int i = 0; i < samples_out.length; i++) {
                samples_out[i].position(samples_out[i].position() + ret * outputChannels * outputDepth);
            }

            if (samples_out[0].position() >= samples_out[0].limit()) {
                frame.nb_samples(audio_input_frame_size);
                avcodec_fill_audio_frame(frame, audio_c.channels(), outputFormat, samples_out[0], samples_out[0].limit(), 0);
                for (int i = 0; i < samples_out.length; i++) {
                    frame.data(i, samples_out[i].position(0));
                    frame.linesize(i, samples_out[i].limit());
                }
                frame.quality(audio_c.global_quality());
                record(frame);
            }
        }
        return frame.key_frame() != 0;
    }

    boolean record(AVFrame frame) throws Exception {
        int ret;

        av_init_packet(audio_pkt);
        audio_pkt.data(audio_outbuf);
        audio_pkt.size(audio_outbuf_size);
        if ((ret = avcodec_encode_audio2(audio_c, audio_pkt, frame, got_audio_packet)) < 0) {
            throw new Exception("avcodec_encode_audio2() error " + ret + ": Could not encode audio packet.");
        }
        if (got_audio_packet[0] != 0) {
            if (audio_pkt.pts() != AV_NOPTS_VALUE) {
                audio_pkt.pts(av_rescale_q(audio_pkt.pts(), audio_c.time_base(), audio_c.time_base()));
            }
            if (audio_pkt.dts() != AV_NOPTS_VALUE) {
                audio_pkt.dts(av_rescale_q(audio_pkt.dts(), audio_c.time_base(), audio_c.time_base()));
            }
            audio_pkt.flags(audio_pkt.flags() | AV_PKT_FLAG_KEY);
            audio_pkt.stream_index(audio_st.index());
        } else {
            return false;
        }

        /* write the compressed frame in the media file */
        synchronized (oc) {
            if (interleaved && video_st != null) {
                if ((ret = av_interleaved_write_frame(oc, audio_pkt)) < 0) {
                    throw new Exception("av_interleaved_write_frame() error " + ret + " while writing interleaved audio frame.");
                }
            } else {
                if ((ret = av_write_frame(oc, audio_pkt)) < 0) {
                    throw new Exception("av_write_frame() error " + ret + " while writing audio frame.");
                }
            }
        }
        return true;
    }
}
