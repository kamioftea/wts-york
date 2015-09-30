$(function(){
	var url = '/status/terror'; //todo reverse routing in JS
	var interval = 5000; // 5 seconds
	var terrorHands = $('.world-terror-dial .hand');

	function updateDial(content)
	{
		var hand = $(content).find('.hand');
		$('.world-terror-dial .hand').replaceWith(hand);
	}

	function getAjaxResponse(request) {
        return Bacon.fromPromise($.ajax(request))
    }

	Bacon.interval(interval, {url: url})
		.flatMap(getAjaxResponse)
		.onValue(updateDial);
});
