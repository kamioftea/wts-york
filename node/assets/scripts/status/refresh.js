$(function () {
	var tweetUrl = '/tweets.json'; //todo reverse routing in JS
	var stateUrl = '/status.json'; //todo reverse routing in JS
	var interval = 1000; // 1 second

	function getAjaxResponse(request) {
		return Bacon.fromPromise($.ajax(request))
	}

	var tweetRequest = {url: tweetUrl};

	var tweetStream = Bacon.once(tweetRequest)
			.merge(Bacon.interval(interval, tweetRequest))
			.flatMap(getAjaxResponse);

    var stateRequest = {url: stateUrl};

    var updateStream =
        Bacon.once(stateRequest)
            .merge(Bacon.interval(interval, stateRequest))
            .flatMap(getAjaxResponse);

	/****************************************************
	 * Tweets
	 ****************************************************/

	$('.tweet-container').each(function(){
		var $container = $(this);
		var template = Handlebars.compile($container.find('.tweet-template').first().html());

		var featuredTweet = null;
		var boldTweets = [];

		updateStream.onValue(function (gameState) {
            boldTweets = gameState.boldTweetIds;
            featuredTweet = Object.assign({}, gameState.featuredTweet, {
                bold:     boldTweets.indexOf(gameState.featuredTweet.id) != -1,
                featured: true
            });
        });

		tweetStream.onValue(function(tweets){

            tweets = tweets
	            .filter(function (val) {
		            return !featuredTweet || featuredTweet.id != val.id
                })
	            .map(function (t) {
                return Object.assign({}, t, {
                    bold:     boldTweets.indexOf(t.id) != -1
                })
            });

            if(featuredTweet)
            {
            	tweets = [featuredTweet].concat(tweets.slice(0, 4))
            }

			$container.html(template({tweets: tweets}))
		})

	});

	/****************************************************
	 * Game Status
	 ****************************************************/

	// Update World terror dial(s)
	$('.world-terror-dial .hand').each(function () {
		var $hand = $(this);

		updateStream.onValue(function (gameStatus) {
			var rotation = gameStatus.terrorLevel === undefined ? -90 : gameStatus.terrorLevel;

			$hand.css({
				transform: 'rotate(' + rotation + 'deg)'
			});
		})
	});

	// Update pr lists
	$('.pr-container').each(function () {
		var $container = $(this);
		var countryName = $container.data('country');
		var $prLabel = $container.find('.pr');
		var $incomeLabel = $container.find('.income');

		updateStream.onValue(function (gameStatus) {
            var newPr = gameStatus.countryPRs !== undefined && gameStatus.countryPRs[countryName] !== undefined
				? gameStatus.countryPRs[countryName]
	            : 0;

            $prLabel.text(newPr);

            var newIncome = gameStatus.countryIncomes !== undefined
	            && gameStatus.countryIncomes[countryName] !== undefined
			    && gameStatus.countryIncomes[countryName][newPr - 1] !== undefined
				? gameStatus.countryIncomes[countryName][newPr - 1]
	            : 0;

            $incomeLabel.text(newIncome);
		});
	});

	/******************************************
	 * Timing Updates
	 *****************************************/
		//Turn(s)
	$('.turn').each(function () {
		var $turnContainer = $(this);

		updateStream.onValue(function (gameStatus) {
			if (gameStatus.turn !== undefined) {
				$turnContainer.html('Turn ' + gameStatus.turn)
			}

		});
	});

	//Phases Name(s)
	$('.phase-name').each(function () {
		var $nameContainer = $(this);

		updateStream.onValue(function (gameStatus) {
			if (gameStatus.phase !== undefined && gameStatus.phase.name !== undefined) {
				$nameContainer.html(gameStatus.phase.name)
			}
		});
	});

	//Activities
	$('dl.phase-activities').each(function () {
		var $activitiesList = $(this);

		updateStream.onValue(function (gameStatus) {
			if (gameStatus.phase !== undefined && gameStatus.phase.activities !== undefined) {
				var $proxyList = $('<dl>');
				var activities = gameStatus.phase.activities;

				for (var group in activities) {
					if (activities.hasOwnProperty(group)) {
						$proxyList.append($('<dt>').append(group));
						$proxyList.append($('<dd>').append(activities[group]));
					}
				}

				$activitiesList.html($proxyList.html());
			}
		});
	});

	// Countdown
	$('.turn-countdown').each(function () {
		var $turnCountdown = $(this);
        var phaseEnd = null;
        var isPaused = true;

        updateStream.onValue(function (gameStatus) {
        	phaseEnd = gameStatus.phaseEnd ? new Date(gameStatus.phaseEnd) : null;
        	isPaused = gameStatus.paused;
        });

        setInterval(
            function () {
                console.log(phaseEnd);
                console.log(isPaused);
                if (phaseEnd && !isPaused) {
                    var now = new Date();
                    var diff = phaseEnd.getTime() - now.getTime();
                    var duration = humanizeDuration(diff, { largest: 1, round: true });

                    console.log(duration);

                    $turnCountdown.text(
                        diff > 0
                            ? duration
                            : '--------'
                    )
                }

                $turnCountdown.css('text-decoration', isPaused ? 'blink' : 'none');
            },
            1000
        )
	});



});
