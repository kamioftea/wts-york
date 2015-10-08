$(function () {
	var url = '/status.json'; //todo reverse routing in JS
	var interval = 1000; // 5 seconds

	function getAjaxResponse(request) {
		return Bacon.fromPromise($.ajax(request))
	}

	var request = {url: url}

	var updateStream =
		Bacon.once(request)
			.merge(Bacon.interval(interval, request))
			.flatMap(getAjaxResponse);

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
	$('.pr-list').each(function () {
		var $list = $(this)
		var countryName = $list.data('country');

		updateStream.onValue(function (gameStatus) {
			if (gameStatus.countryPRs !== undefined && gameStatus.countryPRs[countryName] !== undefined) {
				var newPr = gameStatus.countryPRs[countryName];
				$list.find('.pr-item').removeClass('current');
				$list.find('.pr-item[data-pr-level=' + newPr + ']').addClass('current');
			}
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

});
