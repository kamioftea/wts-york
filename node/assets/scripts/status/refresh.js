$(function(){
	var url = '/status.json'; //todo reverse routing in JS
	var interval = 5000; // 5 seconds

	function getAjaxResponse(request) {
        return Bacon.fromPromise($.ajax(request))
    }

	var request = {url: url}

	var updateStream =
	    Bacon.once(request)
			.merge(Bacon.interval(interval, request))
			.flatMap(getAjaxResponse);

	$('.world-terror-dial .hand').each(function(){
		var $hand = $(this);

		updateStream.onValue(function(gameStatus)
		{
			var rotation = gameStatus.terrorLevel === undefined ? -90 : gameStatus.terrorLevel;

			$hand.css({
            	transform: 'rotate(' + rotation + 'deg)'
            });
		})
	})
});
