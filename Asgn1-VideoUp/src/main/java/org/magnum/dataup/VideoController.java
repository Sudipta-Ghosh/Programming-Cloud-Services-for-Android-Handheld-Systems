/*
 * 
 * Copyright 2014 Jules White
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */
package org.magnum.dataup;

import org.magnum.dataup.model.Video;
import org.magnum.dataup.model.VideoStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import retrofit.http.Streaming;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Controller
public class VideoController {

    private Collection<Video> videoList;
    private VideoFileManager videoFileManager;
    private static final AtomicLong currentId = new AtomicLong(0L);

    @PostConstruct
    public void init() throws IOException {
        videoList = new CopyOnWriteArrayList<Video>();
        videoFileManager = VideoFileManager.get();
    }


    @RequestMapping(value = VideoSvcApi.VIDEO_SVC_PATH, method = RequestMethod.GET)
    @ResponseBody
    public Collection<Video> getVideoList() {
          return videoList;
    }

    @RequestMapping(value=VideoSvcApi.VIDEO_SVC_PATH,method=RequestMethod.POST)
	public @ResponseBody Video addVideo(@RequestBody Video video) {
    	HttpServletRequest request = 
	               ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
		
    	if(video.getId() == 0){
			video.setId(currentId.incrementAndGet());
        }
		 String base = "http://"+request.getServerName() + ((request.getServerPort() != 80) ? ":"+request.getServerPort() : "");
		video.setDataUrl(base);
		videoList.add(video);
		return video;
    }

    @RequestMapping(value = VideoSvcApi.VIDEO_DATA_PATH, method = RequestMethod.POST,
            consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE
    )
    @ResponseBody
    public VideoStatus setVideoData(@PathVariable(VideoSvcApi.ID_PARAMETER) Long id,
                                    @RequestPart(VideoSvcApi.DATA_PARAMETER) MultipartFile videoData,
                                    HttpServletResponse response) {

        VideoStatus videoStatus = new VideoStatus(VideoStatus.VideoState.PROCESSING);

        try {
            Video video = getVideoById(id);
            if (video != null) {
                videoFileManager.saveVideoData(video, videoData.getInputStream());
                videoStatus = new VideoStatus(VideoStatus.VideoState.READY);
            } else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return videoStatus;
    }

    @Streaming
    @RequestMapping(value = VideoSvcApi.VIDEO_DATA_PATH, method = RequestMethod.GET)
    public HttpServletResponse getData(@PathVariable(VideoSvcApi.ID_PARAMETER) long id,
                                       HttpServletResponse response) {
        Video video = getVideoById(id);
        if (video == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        } else {
            if (response.getContentType() == null) {
                response.setContentType("video/mp4");
            }
            try {
                videoFileManager.copyVideoData(video, response.getOutputStream());
            } catch (Exception e) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            }
        }
        return response;
    }

    private Video getVideoById(Long id) {
        for (Video v : videoList) {
            if (v.getId() == id) return v;
        }
        return null;
    }

   
}
