package com.wenming.weiswift.fragment.home.weiboitemdetail.activity;

import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.nostra13.universalimageloader.core.ImageLoader;
import com.sina.weibo.sdk.exception.WeiboException;
import com.sina.weibo.sdk.net.RequestListener;
import com.sina.weibo.sdk.openapi.models.Comment;
import com.sina.weibo.sdk.openapi.models.CommentList;
import com.sina.weibo.sdk.openapi.models.Status;
import com.sina.weibo.sdk.openapi.models.StatusList;
import com.wenming.weiswift.NewFeature;
import com.wenming.weiswift.R;
import com.wenming.weiswift.common.DetailActivity;
import com.wenming.weiswift.common.endlessrecyclerview.EndlessRecyclerOnScrollListener;
import com.wenming.weiswift.common.endlessrecyclerview.HeaderAndFooterRecyclerViewAdapter;
import com.wenming.weiswift.common.endlessrecyclerview.utils.RecyclerViewStateUtils;
import com.wenming.weiswift.common.endlessrecyclerview.weight.LoadingFooter;
import com.wenming.weiswift.common.util.LogUtil;
import com.wenming.weiswift.common.util.NetUtil;
import com.wenming.weiswift.common.util.SDCardUtil;
import com.wenming.weiswift.common.util.ToastUtil;
import com.wenming.weiswift.fragment.home.weiboitemdetail.adapter.CommentAdapter;
import com.wenming.weiswift.fragment.home.weiboitemdetail.adapter.RetweetAdapter;
import com.wenming.weiswift.fragment.home.weiboitemdetail.headview.OnDetailButtonClickListener;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;

/**
 * Created by wenmingvs on 16/4/20.
 */
public abstract class BaseActivity extends DetailActivity {

    public Status mWeiboItem;

    public ArrayList<Comment> mCommentDatas;
    public ArrayList<Status> mRetweetDatas;

    public LinearLayoutManager mLayoutManager;
    public CommentAdapter mCommentAdapter;
    public boolean mNoMoreData;//表示服务器的评论已经加载完成
    public HeaderAndFooterRecyclerViewAdapter mHeaderAndFooterRecyclerViewAdapter;
    public LinearLayout mHeaderView;

    public int mLastestComments;
    public int mLastestReposts;
    public int mLastestAttitudes;

    public RetweetAdapter mRetweetAdapter;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mWeiboItem = getIntent().getParcelableExtra("weiboitem");
        super.onCreate(savedInstanceState);
    }


    @Override
    public void initTitleBar() {
        getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.toolbar_home_weiboitem_detail_title);
        mToolBar = findViewById(R.id.toolbar_home_weiboitem_detail_title);
        mBackIcon = (ImageView) mToolBar.findViewById(R.id.toolbar_back);
        mBackIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    /**
     * 第一次初始化recyclerview
     */
    @Override
    public void initRecyclerView() {
        mLastestComments = mWeiboItem.comments_count;
        mLastestReposts = mWeiboItem.reposts_count;
        mLastestAttitudes = mWeiboItem.attitudes_count;
        mRecyclerView = (RecyclerView) findViewById(R.id.base_RecyclerView);
        mCommentAdapter = new CommentAdapter(mContext, mCommentDatas);
        mHeaderAndFooterRecyclerViewAdapter = new HeaderAndFooterRecyclerViewAdapter(mCommentAdapter);
        mLayoutManager = new LinearLayoutManager(mContext, LinearLayoutManager.VERTICAL, false);
        mRecyclerView.setLayoutManager(mLayoutManager);
        mRecyclerView.setAdapter(mHeaderAndFooterRecyclerViewAdapter);
        addHeaderView(mCommentDatas == null ? 0 : mCommentDatas.size());
        refreshDetailBar(mLastestComments, mLastestReposts, mLastestAttitudes);
    }

    @Override
    public void pullToRefreshData() {
        if (NetUtil.isConnected(mContext)) {
            getWeiBoCount();
            getCommentList();
        } else {
            if (NewFeature.CACHE_DETAIL_ACTIVITY) {
                getWeiBoCount();
                getCommentList();
            }

            mSwipeRefreshLayout.setRefreshing(false);
        }
    }

    protected abstract void addHeaderView(int size);

    /**
     * 异步请求评论，转发，赞数
     */
    public void getWeiBoCount() {
        mSwipeRefreshLayout.setRefreshing(true);
        mStatusesAPI.count(new String[]{mWeiboItem.id}, new RequestListener() {
            @Override
            public void onComplete(String response) {
                if (NewFeature.CACHE_DETAIL_ACTIVITY) {
                    SDCardUtil.put(mContext, SDCardUtil.getSDCardPath() + "/weiSwift/", "count.txt", response);
                    LogUtil.d(response);
                }
                try {
                    JSONArray jsonArray = new JSONArray(response);
                    mLastestComments = jsonArray.getJSONObject(0).optInt("comments");
                    mLastestReposts = jsonArray.getJSONObject(0).optInt("reposts");
                    mLastestAttitudes = jsonArray.getJSONObject(0).optInt("attitudes");
                    refreshDetailBar(mLastestComments, mLastestReposts, mLastestAttitudes);
                } catch (JSONException e) {
                    ToastUtil.showShort(mContext, "刷新评论数失败");
                    e.printStackTrace();
                }
            }

            @Override
            public void onWeiboException(WeiboException e) {
                if (NewFeature.CACHE_DETAIL_ACTIVITY) {
                    String response = SDCardUtil.get(mContext, SDCardUtil.getSDCardPath() + "/weiSwift/", "count.txt");
                    LogUtil.d(response);
                    try {
                        JSONArray jsonArray = new JSONArray(response);
                        mLastestComments = jsonArray.getJSONObject(0).optInt("comments");
                        mLastestReposts = jsonArray.getJSONObject(0).optInt("reposts");
                        mLastestAttitudes = jsonArray.getJSONObject(0).optInt("attitudes");
                    } catch (JSONException e2) {
                        ToastUtil.showShort(mContext, "刷新评论数失败");
                        e2.printStackTrace();
                    }
                }
                ToastUtil.showShort(mContext, e.getMessage());
            }
        });
    }


    protected abstract void refreshDetailBar(int comments, int reposts, int attitudes);

    /**
     * 第一次去请求微博数据
     */
    public void getCommentList() {
        mSwipeRefreshLayout.setRefreshing(true);

        mCommentsAPI.show(Long.valueOf(mWeiboItem.id), 0, 0, NewFeature.GET_COMMENT_ITEM, 1, 0, new RequestListener() {
            @Override
            public void onComplete(String response) {
                if (!TextUtils.isEmpty(response)) {
                    if (NewFeature.CACHE_DETAIL_ACTIVITY) {
                        SDCardUtil.put(mContext, SDCardUtil.getSDCardPath() + "/weiSwift/", "comment.txt", response);
                    }
                    mCommentDatas = CommentList.parse(response).commentList;
                    updateList();
                } else {
                    ToastUtil.showShort(mContext, "返回的微博数据为空");
                }
                mSwipeRefreshLayout.setRefreshing(false);
            }

            @Override
            public void onWeiboException(WeiboException e) {
                if (NewFeature.CACHE_DETAIL_ACTIVITY) {
                    String response = SDCardUtil.get(mContext, SDCardUtil.getSDCardPath() + "/weiSwift/", "comment.txt");
                    LogUtil.d(response);
                    mCommentDatas = CommentList.parse(response).commentList;
                    updateList();
                }
                mSwipeRefreshLayout.setRefreshing(false);
            }
        });
    }

    /**
     * 第一次成功拿到请求的数据
     * 1. 更新recyclerview
     * 2. 更新评论，转发，赞的数量
     */
    @Override
    public void updateList() {
        mRecyclerView.addOnScrollListener(mOnScrollListener);
        mCommentAdapter.setData(mCommentDatas);

        mHeaderAndFooterRecyclerViewAdapter.notifyDataSetChanged();
    }


    public EndlessRecyclerOnScrollListener mOnScrollListener = new EndlessRecyclerOnScrollListener() {
        @Override
        public void onLoadNextPage(View view) {
            super.onLoadNextPage(view);
            LoadingFooter.State state = RecyclerViewStateUtils.getFooterViewState(mRecyclerView);
            if (state == LoadingFooter.State.Loading) {
                Log.d("wenming", "the state is Loading, just wait..");
                return;
            }
            if (!mNoMoreData && mCommentDatas != null) {
                // loading more
                RecyclerViewStateUtils.setFooterViewState(BaseActivity.this, mRecyclerView, mCommentDatas.size(), LoadingFooter.State.Loading, null);
                requestMoreData();
            }
        }

        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            super.onScrollStateChanged(recyclerView, newState);
            switch (newState) {
                case RecyclerView.SCROLL_STATE_DRAGGING:
                    ImageLoader.getInstance().pause();
                    break;
                case RecyclerView.SCROLL_STATE_IDLE:
                    ImageLoader.getInstance().resume();
                    break;
            }
        }


    };

    /**
     * 网络请求下一页的评论数据
     */
    @Override
    public void requestMoreData() {
        //ToastUtil.showShort(mContext, "请求更多评论数据");
        mCommentsAPI.show(Long.valueOf(mWeiboItem.id), 0, Long.valueOf(mCommentDatas.get(mCommentDatas.size() - 1).id), NewFeature.GET_COMMENT_ITEM, 1, 0, new RequestListener() {
            @Override
            public void onComplete(String response) {
                if (!TextUtils.isEmpty(response)) {

                    loadMoreData(response);
                } else {
                    ToastUtil.showShort(mContext, "返回的微博数据为空");
                    mNoMoreData = true;
                }
                mSwipeRefreshLayout.setRefreshing(false);
            }

            @Override
            public void onWeiboException(WeiboException e) {
                ToastUtil.showShort(mContext, e.getMessage());
                if (!NetUtil.isConnected(mContext)) {
                    RecyclerViewStateUtils.setFooterViewState(mRecyclerView, LoadingFooter.State.NetWorkError);
                }
                mSwipeRefreshLayout.setRefreshing(false);
            }
        });
    }

    /**
     * 成功拿到下一页的评论数据，要根据数据的内容来决定是否已经加载到头了
     * 1. 如果请求下来的数据，数目为1，且id和mCommentDatas的最后一条评论的id相同，则表示服务器的数据已经请求完了
     * 2. 如果请求的数据大于1，则删掉重复的第一条，再添加到mCommentDatas中，并且刷新recyclerview的状态和底部的view
     */
    @Override
    public void loadMoreData(String response) {
        ArrayList<Comment> httpRespnse = CommentList.parse(response).commentList;
        if (httpRespnse != null && httpRespnse.size() == 1 && httpRespnse.get(0).id.equals(mCommentDatas.get(mCommentDatas.size() - 1).id)) {
            mNoMoreData = true;
            RecyclerViewStateUtils.setFooterViewState(BaseActivity.this, mRecyclerView, mCommentDatas.size(), LoadingFooter.State.Normal, null);
        } else if (httpRespnse.size() > 1) {
            httpRespnse.remove(0);
            mCommentDatas.addAll(httpRespnse);
            mHeaderAndFooterRecyclerViewAdapter.notifyDataSetChanged();
            RecyclerViewStateUtils.setFooterViewState(mRecyclerView, LoadingFooter.State.Normal);
        }
    }

    public OnDetailButtonClickListener onDetailButtonClickListener = new OnDetailButtonClickListener() {
        @Override
        public void OnComment() {
            //ToastUtil.showShort(mContext, "你点击了评论按钮");
            getNewCommentList();
        }

        @Override
        public void OnRetweet() {
            ToastUtil.showShort(mContext, "API不支持转发功能");

            //getRetweetList();
        }
    };

    /**
     * 点击评论按钮后,
     * 1. 刷新detailbar的数目
     * 2. 获取更新的评论数据，并且添加到顶部
     */
    private void getNewCommentList() {
        mSwipeRefreshLayout.setRefreshing(true);
        getWeiBoCount();
        Long max_id = Long.valueOf(0);
        if (mCommentDatas != null) {
            max_id = Long.valueOf(mCommentDatas.get(0).id);
        }
        mCommentsAPI.show(Long.valueOf(mWeiboItem.id), max_id, 0, NewFeature.GET_COMMENT_ITEM, 1, 0, new RequestListener() {
            @Override
            public void onComplete(String response) {
                if (!TextUtils.isEmpty(response)) {
                    ArrayList<Comment> httpRespnse = CommentList.parse(response).commentList;
                    if (httpRespnse != null && httpRespnse.size() == 1 && httpRespnse.get(0).id.equals(mCommentDatas.get(0).id)) {
                        ToastUtil.showShort(mContext, "没有更新的微博了");
                    } else if (httpRespnse != null && httpRespnse.size() > 1) {
                        httpRespnse.remove(0);
                        if (mCommentDatas == null) {
                            mCommentDatas = new ArrayList<Comment>();
                        }
                        mCommentDatas.addAll(0, httpRespnse);
                        mHeaderAndFooterRecyclerViewAdapter.notifyDataSetChanged();
                    }
                } else {
                    ToastUtil.showShort(mContext, "返回的评论数据为空");
                }
                mSwipeRefreshLayout.setRefreshing(false);
            }

            @Override
            public void onWeiboException(WeiboException e) {
                mSwipeRefreshLayout.setRefreshing(false);
            }
        });


    }

    private void getRetweetList() {
        mSwipeRefreshLayout.setRefreshing(true);
        getWeiBoCount();
        mStatusesAPI.repostTimeline(Long.valueOf(mWeiboItem.id), 0, 0, NewFeature.GET_RETWEET_ITEM, 1, 0, new RequestListener() {
            @Override
            public void onComplete(String response) {
                if (!TextUtils.isEmpty(response)) {
                    ArrayList<Status> httpRespnse = StatusList.parse(response).statusList;
                    LogUtil.d(response);
                    ArrayList<Comment> transfData = new ArrayList<Comment>();
                    Comment comment = new Comment();
                    for (Status status : httpRespnse) {
                        comment.user = status.user;
                        comment.created_at = status.created_at;
                        comment.text = status.text;
                        transfData.add(comment);
                    }
                    mCommentDatas = transfData;
                    mCommentAdapter.setData(mCommentDatas);
                    mHeaderAndFooterRecyclerViewAdapter.notifyDataSetChanged();
                    refreshDetailBar(mLastestComments, mLastestReposts, mLastestAttitudes);

                } else {
                    ToastUtil.showShort(mContext, "返回的转发数据为空");
                }

                mSwipeRefreshLayout.setRefreshing(false);
            }

            @Override
            public void onWeiboException(WeiboException e) {
                mSwipeRefreshLayout.setRefreshing(false);
            }
        });

    }


}
