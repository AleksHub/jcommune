/**
 * Copyright (C) 2011  JTalks.org Team
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.jtalks.jcommune.web.controller;

import org.jtalks.jcommune.model.dto.PageRequest;
import org.jtalks.jcommune.model.entity.*;
import org.jtalks.jcommune.service.*;
import org.jtalks.jcommune.service.exceptions.NotFoundException;
import org.jtalks.jcommune.service.nontransactional.LocationService;
import org.jtalks.jcommune.web.dto.Breadcrumb;
import org.jtalks.jcommune.web.dto.TopicDto;
import org.jtalks.jcommune.web.util.BreadcrumbBuilder;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.ModelAndView;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.*;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.springframework.test.web.ModelAndViewAssert.*;
import static org.testng.Assert.*;

/**
 * @author Teterin Alexandre
 * @author Kirill Afonin
 * @author Max Malakhov
 * @author Eugeny Batov
 */
public class TopicControllerTest {
    public long BRANCH_ID = 1L;
    private long TOPIC_ID = 1L;
    private String TOPIC_CONTENT = "content here";

    private JCUser user;
    private Branch branch;

    @Mock
    private TopicModificationService topicModificationService;
    @Mock
    private TopicFetchService topicFetchService;
    @Mock
    private PostService postService;
    @Mock
    private BranchService branchService;
    @Mock
    private UserService userService;
    @Mock
    private BreadcrumbBuilder breadcrumbBuilder;
    @Mock
    private LocationService locationService;
    @Mock
    private SessionRegistry registry;
    @Mock
    private LastReadPostService lastReadPostService;

    private TopicController controller;

    @BeforeMethod
    public void initEnvironment() {
        initMocks(this);
        controller = new TopicController(
                topicModificationService,
                postService,
                branchService,
                lastReadPostService,
                userService,
                breadcrumbBuilder,
                locationService,
                registry, topicFetchService);
    }

    @BeforeMethod
    public void prepareTestData() {
        branch = new Branch("", "description");
        branch.setId(BRANCH_ID);
        user = new JCUser("username", "email@mail.com", "password");
    }

    @Test
    public void initBinder() {
        WebDataBinder binder = mock(WebDataBinder.class);
        controller.initBinder(binder);
        verify(binder).registerCustomEditor(eq(String.class), any(StringTrimmerEditor.class));
    }

    @Test
    public void delete() throws NotFoundException {
        Topic topic = new Topic(null, null);
        branch.addTopic(topic);
        when(topicFetchService.get(anyLong())).thenReturn(topic);

        ModelAndView actualMav = controller.deleteTopic(TOPIC_ID);

        assertViewName(actualMav, "redirect:/branches/" + BRANCH_ID);
        verify(topicModificationService).deleteTopic(topic);
    }
    //TODO: this has to be improved - too complicated test!=
    @Test
    public void showTopicPageShouldShowListOfPostsWithUpdatedInfoAboutLastReadPosts() throws NotFoundException {
        String page = "1";
        Topic topic = createTopic();
        prepareViewTopicMocks(topic, page);

        WebRequest request = mock(WebRequest.class);

        ModelAndView mav = controller.showTopicPage(request, TOPIC_ID, page);

        verify(topicFetchService).checkViewTopicPermission(topic.getBranch().getId());
        verify(lastReadPostService).markTopicPageAsRead(topic, Integer.valueOf(page));
        //
        assertViewName(mav, "topic/postList");
        assertAndReturnModelAttributeOfType(mav, "postsPage", Page.class);
        //
        Topic actualTopic = assertAndReturnModelAttributeOfType(mav, "topic", Topic.class);
        assertEquals(actualTopic, topic);
        assertModelAttributeAvailable(mav, "breadcrumbList");
    }

    @Test
    public void showTopicPageShouldReturnNullIfIfModifiedSinceOlderThenLastUpdate() throws NotFoundException {
        String page = "1";
        Topic topic = createTopic();
        prepareViewTopicMocks(topic, page);

        WebRequest request = mock(WebRequest.class);
        when(request.checkNotModified(topic.getModificationDate().getMillis())).thenReturn(true);

        ModelAndView mav = controller.showTopicPage(request, TOPIC_ID, page);

        assertNull(mav);
    }

    @Test
    public void showTopicPageShouldReturnNotNullDataIfIfModifiedSinceOlderThenLastUpdate() throws NotFoundException {
        String page = "1";
        Topic topic = createTopic();
        prepareViewTopicMocks(topic, page);

        WebRequest request = mock(WebRequest.class);
        when(request.checkNotModified(topic.getModificationDate().getMillis())).thenReturn(false);

        ModelAndView mav = controller.showTopicPage(request, TOPIC_ID, page);

        assertNotNull(mav);
    }

    @Test
    public void createTopicShouldPassAndRedirectToNewTopicIfItIsValid() throws Exception {
        Branch branch = createBranch();
        Topic topic = createTopic();
        TopicDto dto = getDto();
        BindingResult result = mock(BindingResult.class);
        when(branchService.get(BRANCH_ID)).thenReturn(branch);
        when(topicModificationService.createTopic(topic, TOPIC_CONTENT)).thenReturn(topic);

        ModelAndView mav = controller.createTopic(dto, result, BRANCH_ID);

        verify(lastReadPostService).markTopicAsRead(topic);
        verify(topicModificationService).createTopic(topic, TOPIC_CONTENT);
        //
        assertViewName(mav, "redirect:/topics/1");
    }

    @Test
    public void createTopicShouldNotPassAndMustShowTopicErrorIfItIsInvalid() throws NotFoundException {
        BindingResult result = mock(BindingResult.class);
        when(result.hasErrors()).thenReturn(true);
        when(branchService.get(BRANCH_ID)).thenReturn(branch);
        when(breadcrumbBuilder.getForumBreadcrumb(branch)).thenReturn(new ArrayList<Breadcrumb>());

        ModelAndView mav = controller.createTopic(getDto(), result, BRANCH_ID);

        verify(branchService).get(BRANCH_ID);
        verify(breadcrumbBuilder).getForumBreadcrumb(branch);
        //
        assertViewName(mav, "topic/topicForm");
        long branchId = assertAndReturnModelAttributeOfType(mav, "branchId", Long.class);
        assertEquals(branchId, BRANCH_ID);
    }

    @Test
    public void showNewTopicPageShouldReturnTemplateForNewTopic() throws NotFoundException {
        when(branchService.get(BRANCH_ID)).thenReturn(branch);
        when(breadcrumbBuilder.getNewTopicBreadcrumb(branch)).thenReturn(new ArrayList<Breadcrumb>());

        ModelAndView mav = controller.showNewTopicPage(BRANCH_ID);

        verify(branchService).get(BRANCH_ID);
        verify(breadcrumbBuilder).getNewTopicBreadcrumb(branch);
        //
        assertViewName(mav, "topic/topicForm");
        //
        TopicDto topicDto = assertAndReturnModelAttributeOfType(mav, "topicDto", TopicDto.class);
        Topic actualTopic = topicDto.getTopic();
        Branch actualTopicBranch = actualTopic.getBranch();
        assertEquals(actualTopicBranch, branch, "Topic template should be attached to branch where user creates branch.");
        assertNotNull(actualTopic.getPoll(), "Topic template should contain poll template.");

        //
        long branchId = assertAndReturnModelAttributeOfType(mav, "branchId", Long.class);
        assertEquals(branchId, BRANCH_ID,
                "Topic template should be returned with the same branch id as passed to create new topic.");
        assertModelAttributeAvailable(mav, "breadcrumbList");
    }

    @Test
    public void editTopicPageShouldOpenEditFormAndNotEnableNotificationsIfUserNotSubcribed() throws NotFoundException {
        Topic topic = this.createTopic();
        Post post = new Post(user, "content");
        topic.addPost(post);
        //
        when(topicFetchService.get(TOPIC_ID)).thenReturn(topic);
        when(breadcrumbBuilder.getForumBreadcrumb(topic)).thenReturn(new ArrayList<Breadcrumb>());
        when(userService.getCurrentUser()).thenReturn(user);

        ModelAndView mav = controller.editTopicPage(TOPIC_ID);

        verify(topicFetchService).get(TOPIC_ID);
        verify(breadcrumbBuilder).getForumBreadcrumb(topic);
        //
        assertViewName(mav, "topic/topicForm");
        //
        TopicDto dto = assertAndReturnModelAttributeOfType(mav, "topicDto", TopicDto.class);
        assertEquals(dto.getTopic().getId(), TOPIC_ID);
        //
        long branchId = assertAndReturnModelAttributeOfType(mav, "branchId", Long.class);
        assertEquals(branchId, BRANCH_ID);
        assertModelAttributeAvailable(mav, "breadcrumbList");
    }

    @Test
    public void editTopicPageShouldOpenEditFormAndEnableNotificationsIfUserSubcribed() throws NotFoundException {
        Topic topic = this.createTopic();
        Set<JCUser> subscribers = new HashSet<JCUser>();
        subscribers.add(user);
        topic.setSubscribers(subscribers);
        Post post = new Post(user, "content");
        topic.addPost(post);
        //
        when(topicFetchService.get(TOPIC_ID)).thenReturn(topic);
        when(breadcrumbBuilder.getForumBreadcrumb(topic)).thenReturn(new ArrayList<Breadcrumb>());
        when(userService.getCurrentUser()).thenReturn(user);

        ModelAndView mav = controller.editTopicPage(TOPIC_ID);

        verify(topicFetchService).get(TOPIC_ID);
        verify(breadcrumbBuilder).getForumBreadcrumb(topic);
        //
        assertViewName(mav, "topic/topicForm");
        //
        TopicDto dto = assertAndReturnModelAttributeOfType(mav, "topicDto", TopicDto.class);
        assertEquals(dto.getTopic().getId(), TOPIC_ID);
        //
        long branchId = assertAndReturnModelAttributeOfType(mav, "branchId", Long.class);
        assertEquals(branchId, BRANCH_ID);
        assertModelAttributeAvailable(mav, "breadcrumbList");
    }

    @Test(expectedExceptions = AccessDeniedException.class)
    public void editTopicPageShouldNotBePossibleForCodeReview() throws NotFoundException {
        Topic topic = this.createTopic();
        topic.setCodeReview(new CodeReview());
        when(topicFetchService.get(TOPIC_ID)).thenReturn(topic);
        when(breadcrumbBuilder.getForumBreadcrumb(topic)).thenReturn(new ArrayList<Breadcrumb>());
        when(userService.getCurrentUser()).thenReturn(user);

        controller.editTopicPage(TOPIC_ID);
    }

    @Test
    public void saveValidationPass() throws NotFoundException {
        TopicDto dto = getDto();
        BindingResult bindingResult = new BeanPropertyBindingResult(dto, "topicDto");
        when(topicFetchService.get(TOPIC_ID)).thenReturn(createTopic());

        //invoke the object under test
        ModelAndView mav = controller.editTopic(dto, bindingResult, TOPIC_ID);
        Topic topic = topicFetchService.get(TOPIC_ID);
        //check expectations
        verify(topicModificationService).updateTopic(topic, dto.getPoll());

        //check result
        assertViewName(mav, "redirect:/topics/" + TOPIC_ID);
    }

    @Test
    public void saveValidationFail() throws NotFoundException {
        TopicDto dto = getDto();
        BeanPropertyBindingResult resultWithErrors = mock(BeanPropertyBindingResult.class);
        when(topicFetchService.get(TOPIC_ID)).thenReturn(this.createTopic());
        when(resultWithErrors.hasErrors()).thenReturn(true);

        ModelAndView mav = controller.editTopic(dto, resultWithErrors, TOPIC_ID);

        assertViewName(mav, "topic/topicForm");
        long branchId = assertAndReturnModelAttributeOfType(mav, "branchId", Long.class);
        assertEquals(branchId, BRANCH_ID);

        verify(topicModificationService, never()).updateTopic(Matchers.<Topic>any(), Matchers.<Poll>any());
    }

    @Test
    public void moveTopic() throws NotFoundException {
        Topic topic = createTopic();
        when(topicFetchService.get(TOPIC_ID)).thenReturn(topic);

        controller.moveTopic(TOPIC_ID, BRANCH_ID);

        verify(topicModificationService).moveTopic(topic, BRANCH_ID);
    }

    @Test
    public void closeTopic() throws NotFoundException {
        Topic topic = createTopic();
        when(topicFetchService.get(TOPIC_ID)).thenReturn(topic);

        controller.closeTopic(TOPIC_ID);

        verify(topicModificationService).closeTopic(topic);
    }

    @Test
    public void reopenTopic() throws NotFoundException {
        Topic topic = createTopic();
        when(topicFetchService.get(TOPIC_ID)).thenReturn(topic);

        controller.openTopic(TOPIC_ID);

        verify(topicModificationService).openTopic(topic);
    }

    private Branch createBranch() {
        Branch branch = new Branch("branch name", "branch description");
        branch.setId(BRANCH_ID);
        return branch;
    }

    private Topic createTopic() {
        Branch branch = createBranch();
        Topic topic = new Topic(user, "Topic theme");
        topic.setId(TOPIC_ID);
        topic.setUuid("uuid");
        topic.setBranch(branch);
        topic.addPost(new Post(user, TOPIC_CONTENT));
        return topic;
    }

    private TopicDto getDto() {
        TopicDto dto = new TopicDto();
        Topic topic = createTopic();
        dto.setBodyText(TOPIC_CONTENT);
        Poll poll = new Poll();
        List<PollItem> pollItems = Arrays.asList(new PollItem("123"));
        poll.addPollOptions(pollItems);
        topic.setPoll(poll);
        dto.setTopic(topic);
        return dto;
    }


    private void prepareViewTopicMocks(Topic topic, String page) throws NotFoundException {
        PageRequest pageable = new PageRequest(page, 15);
        Page<Post> postsPage = new PageImpl<>(topic.getPosts(), pageable, 30L);
        when(userService.getCurrentUser()).thenReturn(topic.getTopicStarter());
        when(topicFetchService.get(TOPIC_ID)).thenReturn(topic);
        when(breadcrumbBuilder.getForumBreadcrumb(topic)).thenReturn(new ArrayList<Breadcrumb>());
        when(postService.getPosts(topic, page)).thenReturn(postsPage);
    }
}
