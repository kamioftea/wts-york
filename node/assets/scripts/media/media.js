$(function () {
    var tweetUrl = '/tweets.json?n=20'; //todo reverse routing in JS
    var stateUrl = '/status.json'; //todo reverse routing in JS
    var toggleBoldUrl = '/media/toggle-bold'; //todo reverse routing in JS
    var interval = 1000; // 1 second

    function getAjaxResponse(request) {
        return Bacon.fromPromise($.ajax(request))
    }

    var stateRequest = {url: stateUrl};

    var updateStream =
        Bacon.once(stateRequest)
            .merge(Bacon.interval(interval, stateRequest))
            .flatMap(getAjaxResponse);

    var tweetRequest = {url: tweetUrl};

    var tweetStream = Bacon.once(tweetRequest)
        .merge(Bacon.interval(interval, tweetRequest))
        .flatMap(getAjaxResponse);

    var $container = $('.tweet-container');
    var template = Handlebars.compile($('.tweet-template').first().html());

    var featuredTweet = null;
    var boldTweets = [];

    updateStream.onValue(function (gameState) {
        featuredTweet = gameState.featuredTweet;
        boldTweets = gameState.boldTweetIds;
    });

    tweetStream.onValue(function (tweets) {
        $container.html(template({
            tweets: tweets.map(function (t) {
                return Object.assign({}, t, {
                    bold:     boldTweets.indexOf(t.id) != -1,
                    featured: featuredTweet && t.id == featuredTweet.id
                })
            })
        }))
    });

    $container.on('click', '.toggle-bold', function (ev) {
        ev.preventDefault();
        var tweet = $(this).closest('.tweet');
        $.ajax({
            url:  [
                      toggleBoldUrl,
                      tweet.data('tweetId'),
                      tweet.hasClass('text-bold') ? 'false' : 'true'
                  ].join('/'),
            type: 'post'
        })
    });

});
