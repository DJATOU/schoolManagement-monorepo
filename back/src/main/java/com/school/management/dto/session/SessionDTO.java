package com.school.management.dto.session;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionDTO {

    private Long id;

    @NotNull(message = "Title is required")
    @Size(min = 1, max = 255, message = "Title length must be between 1 and 255 characters")
    private String title;

    @NotNull(message = "Session type is required")
    private String sessionType;

    @NotNull(message = "Feedback link is required")
    private String feedbackLink;

    private Boolean isFinished;

    @NotNull(message = "Session start time is required")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX")
    private Date sessionTimeStart;

    @NotNull(message = "Session end time is required")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX")
    private Date sessionTimeEnd;

    @NotNull(message = "Group ID is required")
    @JsonProperty("groupId")
    private Long groupId;

    @JsonProperty("groupName") // Add group name
    private String groupName;

    @NotNull(message = "Teacher ID is required")
    @JsonProperty("teacherId")
    private Long teacherId;

    @JsonProperty("teacherName") // Add teacher name
    private String teacherName;

    @NotNull(message = "Room ID is required")
    @JsonProperty("roomId")
    private Long roomId;

    @JsonProperty("roomName") // Add room name
    private String roomName;

    @NotNull(message = "Series ID is required")
    @JsonProperty("sessionSeriesId")
    private Long sessionSeriesId;

    @JsonProperty("seriesName") // Add series name
    private String seriesName;
}
